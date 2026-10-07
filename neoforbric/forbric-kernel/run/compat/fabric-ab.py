#!/usr/bin/env python3
"""A pure-Fabric pack on a native Fabric server and on Forbric: same jars, same world settings, server side.

    fabric-ab.py --data DIR --out DIR pack     [--subjects all|native-pass|matched-pass] [--ticks 1200] [--timeout 1800] [--xmx 4G]
    fabric-ab.py --data DIR --out DIR per-mod  [--ticks 200] [--timeout 900] [--xmx 2G] [--jobs 2] [--only JAR ...]
    fabric-ab.py --data DIR --out DIR confirm  [same limits as per-mod]
    fabric-ab.py --data DIR --out DIR ddmin    [--subjects ...] [--ticks 1200] [--timeout 1800] [--xmx 4G] [--budget 60]
    fabric-ab.py --data DIR --out DIR summary  [--report DIR]

DIR (--data) is a pick.py data directory (manifest.json, closure.json, mods/); every jar is checked against the
manifest's digest before anything runs. Each session is one `native-controls.py run-set` in a fresh instance, and
every finished session is a line of <out>/runs.jsonl keyed by engine, mod-set SHA-256, ticks and the engine's own
identity (the kernel jar's SHA-256 for Forbric, the launcher's for native), so a repeated command runs only what it
has not seen. The Forbric arm is this checkout's kernel; a runs.jsonl line from another kernel is never reused.

pack runs every jar together on both arms; --subjects native-pass combines only the subjects that ran alone on
native Fabric (with their dependencies), matched-pass those that ran alone on both arms. per-mod runs each subject (popular, random) with its closure.json
dependencies on both arms. confirm reruns, one at a time, every per-mod pair whose arms disagree, since a STALL on a
loaded machine is not a verdict. ddmin minimises the pack's Forbric failure (ddmin_core over the subjects, the oracle
being the Forbric arm, the failure being the pack session's signature) and then runs the minimal set on native too.
summary writes summary.json: no local paths, only jar names, digests, outcomes and signatures.
"""
import argparse
import concurrent.futures
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import threading
import time

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "mac"))
import ddmin_core  # noqa: E402
from ddmin_core import FAIL, PASS, UNRESOLVED  # noqa: E402

_spec = importlib.util.spec_from_file_location("native_controls", HERE / "native-controls.py")
nc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(nc)

SUBJECT_KINDS = ("popular", "random")
MATCHED_PASS, FORBRIC_ONLY, NATIVE_ONLY, BOTH_FAIL, INPUT_MISMATCH = \
    "MATCHED_PASS", "FORBRIC_ONLY", "NATIVE_ONLY", "BOTH_FAIL", "INPUT_MISMATCH"


# --- pure parts ---------------------------------------------------------------------------------------------------

def pair_verdict(native, forbric):
    """What one native/Forbric pair of run-set results says. Different bytes answer nothing."""
    if native["modSetSha256"] != forbric["modSetSha256"]:
        return INPUT_MISMATCH
    ok = (native["outcome"] == nc.DONE, forbric["outcome"] == nc.DONE)
    return {(True, True): MATCHED_PASS, (True, False): FORBRIC_ONLY, (False, True): NATIVE_ONLY}.get(ok, BOTH_FAIL)


def judge(reference_signature, result):
    """ddmin's verdict on one Forbric session: PASS when it ran, FAIL when it failed like the pack, else UNRESOLVED."""
    if result["outcome"] == nc.DONE:
        return PASS
    return FAIL if result.get("signature") == reference_signature else UNRESOLVED


def subjects(manifest):
    return [row["filename"] for row in manifest if row["kind"] in SUBJECT_KINDS]


def session_key(engine, digest, ticks, identity):
    return hashlib.sha256(json.dumps([engine, digest, ticks, identity]).encode()).hexdigest()


def identity_digest(result):
    ident = result.get("identity") or {}
    return (ident.get("kernel") or ident.get("launcher") or {}).get("sha256")


def latest_pairs(records, labels):
    """The last native and Forbric record of each label (later lines win), as {label: {engine: record}}."""
    out = {}
    for record in records:
        if record["label"] in labels:
            out.setdefault(record["label"], {})[record["engine"]] = record
    return out


def summarise(manifest, records, kernel, launcher, ticks):
    """summary.json: per-pair verdicts for the pack and every subject, counted; no paths."""
    names = subjects(manifest)
    by_label = {}
    for record in records:
        by_label.setdefault(record["label"], []).append(record)

    def arm(record):
        return None if record is None else {k: record.get(k) for k in ("outcome", "signature", "modSetSha256", "jars", "seconds",
                                                                         "secondsToDone", "gametime", "exitCode", "lingeredAfterStop",
                                                                         "otherThreadFailures")}

    def pair(label):
        runs = by_label.get(label, [])
        native = next((r for r in reversed(runs) if r["engine"] == "native" and r.get("identityDigest") == launcher), None)
        forbric = next((r for r in reversed(runs) if r["engine"] == "forbric" and r.get("identityDigest") == kernel), None)
        attempts = [dict(engine=r["engine"], outcome=r["outcome"], signature=r.get("signature")) for r in runs
                    if r.get("identityDigest") in (kernel, launcher) and "cachedFrom" not in r]
        verdict = pair_verdict(native, forbric) if native and forbric else "NOT_RUN"
        return dict(verdict=verdict, native=arm(native), forbric=arm(forbric), attempts=attempts)
    per_mod = {name: pair("per-mod:" + name) for name in names}
    counts = {}
    for row in per_mod.values():
        counts[row["verdict"]] = counts.get(row["verdict"], 0) + 1
    return dict(kernelSha256=kernel, nativeLauncherSha256=launcher, subjects=len(names), jars=len(manifest),
                pack=pair("pack"), packs={label: pair(label) for label in PACKS.values() if label in by_label}, perModCounts=counts,
                perModNotMatched={name: row for name, row in per_mod.items() if row["verdict"] != MATCHED_PASS},
                perModMatchedPass=sorted(name for name, row in per_mod.items() if row["verdict"] == MATCHED_PASS))


# --- running ------------------------------------------------------------------------------------------------------

def run_set_process(engine, jars, ticks, timeout, xmx):
    """One `native-controls.py run-set` in its own process (so a cancel takes its server along); its result.json."""
    command = [sys.executable, str(HERE / "native-controls.py"), "run-set", "--engine", engine, "--ticks", str(ticks),
               "--timeout", str(timeout), "--xmx", xmx, "--mods", *map(str, jars)]
    done = subprocess.run(command, capture_output=True, text=True)
    line = next((l for l in reversed(done.stdout.splitlines()) if l.startswith("{")), None)
    if line is None:
        raise RuntimeError(f"run-set gave no result for {engine}: {done.stdout[-1500:]} {done.stderr[-1500:]}")
    return json.loads(Path(json.loads(line)["result"]).read_text())


class Lab:
    def __init__(self, data, out, ticks, timeout, xmx, kernel=None, launcher=None, launch=run_set_process, verbose=True):
        self.data, self.out = Path(data).resolve(), Path(out).resolve()
        self.out.mkdir(parents=True, exist_ok=True)
        self.manifest = json.loads((self.data / "manifest.json").read_text())
        self.closure = json.loads((self.data / "closure.json").read_text())
        self.ticks, self.timeout, self.xmx = ticks, timeout, xmx
        self.mods = self.data / "mods"
        names = [row["filename"] for row in self.manifest]
        if len(set(names)) != len(names):
            raise SystemExit("the manifest names a jar twice")
        outside = sorted({dep for deps in self.closure.values() for dep in deps} - set(names))
        if outside:
            raise SystemExit("closure.json needs jars the manifest does not hold: " + ", ".join(outside))
        for row in self.manifest:
            data = (self.mods / row["filename"]).read_bytes()
            if hashlib.sha1(data).hexdigest() != row["sha1"] or len(data) != row["size"]:
                raise SystemExit(f"{row['filename']} is not the manifest's bytes")
        self.digests = {name: nc.sha(self.mods / name) for name in names}
        self.kernel = kernel or nc.sha(nc.kernel_jar())
        self.launcher = launcher or nc.sha(nc.BASE / "native/fabric" / nc.FABRIC_LAUNCHER)
        self.launch, self.verbose = launch, verbose
        self.runs = self.out / "runs.jsonl"
        self.lock = threading.Lock()

    def records(self):
        if not self.runs.is_file():
            return []
        return [json.loads(line) for line in self.runs.read_text().splitlines() if line.strip()]

    def set_digest(self, jars):
        return hashlib.sha256("\n".join(sorted(self.digests[j] for j in jars)).encode()).hexdigest()

    def run(self, label, engine, jars, ticks=None, timeout=None, xmx=None, fresh=False):
        """One run-set session, or the cached record of an identical one."""
        ticks = self.ticks if ticks is None else ticks
        ident = self.kernel if engine == "forbric" else self.launcher
        key = session_key(engine, self.set_digest(jars), ticks, ident)
        if not fresh:
            same = [record for record in self.records() if record["key"] == key]
            mine = [record for record in same if record["label"] == label]
            if mine:
                return mine[-1]
            if same:
                # Recorded under this label too, so the summary finds it, but marked: it is not another launch.
                record = dict(same[-1], label=label, cachedFrom=same[-1].get("cachedFrom", same[-1]["label"]))
                self.append(record)
                return record
        result = self.launch(engine, [self.mods / j for j in jars], ticks, timeout or self.timeout, xmx or self.xmx)
        if identity_digest(result) != ident:
            raise SystemExit(f"{label}/{engine} ran {identity_digest(result)}, not {ident}: the kernel changed during the run")
        if result["modSetSha256"] != self.set_digest(jars) or result["inputDrift"]:
            raise SystemExit(f"{label}/{engine} did not run the manifest's bytes")
        record = dict(key=key, label=label, engine=engine, jars=len(jars), ticks=ticks, identityDigest=ident,
                      **{k: result[k] for k in ("outcome", "signature", "modSetSha256", "seconds", "secondsToDone", "gametime",
                                                "exitCode", "lingeredAfterStop", "otherThreadFailures", "crashReports", "result")})
        self.append(record)
        if self.verbose:
            print(f"{label:60} {engine:8} {record['outcome']:16} {record['seconds']:7.1f}s {record.get('signature') or ''}", flush=True)
        return record

    def append(self, record):
        with self.lock:
            with self.runs.open("a") as handle:
                handle.write(json.dumps(record) + "\n")

    def closed(self, jars):
        return ddmin_core.closed(jars, self.closure)


PACKS = {"all": "pack", "native-pass": "pack-native-pass", "matched-pass": "pack-matched-pass"}


def pack_subjects(manifest, records, kernel, launcher, mode):
    """The subjects a pack combines: all of them, those that ran alone on native Fabric, or those that ran alone on both.

    The narrower packs ask what a whole pack cannot when native Fabric itself refuses some subjects: whether the
    mods a native server does run still run together on Forbric. A subject whose own session is missing on an arm
    the mode reads makes the selection incomplete, which is refused rather than read as a failure.
    """
    names = subjects(manifest)
    if mode == "all":
        return names
    current = [r for r in records if r.get("identityDigest") in (kernel, launcher)]
    pairs = latest_pairs(current, {"per-mod:" + name for name in names})
    engines = ("native",) if mode == "native-pass" else ("native", "forbric")
    missing = [name for name in names if any(e not in pairs.get("per-mod:" + name, {}) for e in engines)]
    if missing:
        raise SystemExit(f"per-mod is incomplete for {len(missing)} subjects, e.g. {missing[0]}")
    return [name for name in names if all(pairs["per-mod:" + name][e]["outcome"] == nc.DONE for e in engines)]


def pack_jars(lab, mode):
    chosen = pack_subjects(lab.manifest, lab.records(), lab.kernel, lab.launcher, mode)
    if mode == "all":
        return [row["filename"] for row in lab.manifest], chosen
    return lab.closed(chosen), chosen


def per_mod(lab, jobs, only=None, fresh=False):
    names = only or subjects(lab.manifest)
    tasks = [(name, engine) for name in names for engine in ("native", "forbric")]
    with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as pool:
        futures = [pool.submit(lab.run, "per-mod:" + name, engine, lab.closed([name]), fresh=fresh) for name, engine in tasks]
        for future in concurrent.futures.as_completed(futures):
            future.result()


def confirm(lab):
    pairs = latest_pairs([r for r in lab.records() if r.get("identityDigest") in (lab.kernel, lab.launcher)],
                         {"per-mod:" + name for name in subjects(lab.manifest)})
    for label, arms in sorted(pairs.items()):
        if len(arms) == 2 and pair_verdict(arms["native"], arms["forbric"]) not in (MATCHED_PASS, BOTH_FAIL):
            name = label.split(":", 1)[1]
            for engine in ("native", "forbric"):
                lab.run(label, engine, lab.closed([name]), fresh=True)


# A library more candidates than this need says nothing about which of them failed.
SEED_DEPENDENTS = 3


def seed_candidates(named, candidates, closure):
    """The evidence's jars as candidates. A jar the evidence names that is only a dependency (a library whose
    mixin or entrypoint failed, a nested mod) stands for the few candidates that pull it in, since ddmin can only
    take candidates out; without this the seed set misses the failure and the search starts from the whole pack."""
    allowed, out = set(candidates), []
    for jar in named:
        if jar in allowed:
            out.append(jar)
            continue
        dependents = [c for c in candidates if jar in ddmin_core.closed([c], closure)]
        if len(dependents) <= SEED_DEPENDENTS:
            out += dependents
    return list(dict.fromkeys(out))


def minimise(lab, budget, mode="all"):
    """ddmin over a pack's Forbric failure, then the minimal set on native: who else fails on it says whose it is."""
    names, chosen = pack_jars(lab, mode)
    reference = lab.run(PACKS[mode], "forbric", names)
    native = lab.run(PACKS[mode], "native", names)
    if reference["outcome"] == nc.DONE:
        return dict(status="PASSED", pack=PACKS[mode], reference=reference["signature"], nativePack=native["outcome"])
    needed = {dep for jar in chosen for dep in lab.closure.get(jar, ())}
    candidates = chosen + [j for j in names if j not in chosen and j not in needed]
    launches = [0]

    def oracle(config):
        launches[0] += 1
        if launches[0] > budget:
            raise RuntimeError("budget")
        record = lab.run(f"ddmin:{len(config)}:{lab.set_digest(config)[:12]}", "forbric", config)
        return judge(reference["signature"], record)
    evidence = Path(reference["result"]).parent
    crash = next(iter(sorted((evidence / "crash-reports").glob("*.txt"))), None)
    read = lambda p: p.read_text(errors="replace") if p and p.is_file() else ""
    named = ddmin_core.seeds(read(crash), read(evidence / ".forbric-kernel/crash-analysis.txt"),
                             read(evidence / ".forbric-kernel/compatibility-report.json"), names)
    seeds = seed_candidates(named, candidates, lab.closure)
    try:
        reduction = ddmin_core.minimise(candidates, oracle, lab.closure, seeds)
    except ddmin_core.NotReproduced as failure:
        return dict(status="NOT_REPRODUCED", pack=PACKS[mode], reference=reference["signature"], detail=str(failure))
    except RuntimeError as failure:
        if str(failure) != "budget":
            raise
        return dict(status="BUDGET", pack=PACKS[mode], reference=reference["signature"])
    native_minimal = lab.run("ddmin-minimal", "native", reduction.closed)
    forbric_minimal = lab.run("ddmin-minimal", "forbric", reduction.closed)
