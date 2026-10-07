#!/usr/bin/env python3
"""Minimise a failing mixed pack in the game: ddmin_core's search, with one client session for every configuration.

    ddmin.py --manifest <mixed manifest.json> [--out DIR] [--ticks 200] [--stall 120] [--timeout 420] [--grace 20]
             [--jvm=-D... ...] [--budget 80] [--no-seeds] [--iterate] [--narrow]

PERMOD_DATA holds mods/ and closure.json; PERMOD_MC, FORBRIC_VERSION and FORBRIC_JAVA select the installed profile
as for per-mod.py; PERMOD_DDMIN_INSTANCE names the disposable instance under PERMOD_DATA (default ddmin-inst).
Everything lands in <out>/ddmin/ (out defaults to PERMOD_DATA): cache.jsonl, runs/<label>/ and ddmin-result.json.

The pack is first run whole, and that session is the reference: its failure signature is what FAIL means, and the
mods it loaded, from which jar, are the arbitration every other session must reproduce. A session whose winners
differ ran a different program, so it is UNRESOLVED whatever it printed.
"""
import argparse
from dataclasses import dataclass
import hashlib
import io
import json
import os
from pathlib import Path
import re
import sys
import tomllib
import zipfile
import ddmin_core
from ddmin_core import FAIL, UNRESOLVED, NotReproduced
import mixed

DISABLE_CONFIGS = 'forbric.disableMixinConfigs'
SUPPRESS_MIXINS = 'forbric.suppressMixins'
_EXIT = re.compile(r'client exit=(-?\d+)')
_PICK = re.compile(r'([A-Za-z0-9_.\-]+)\s*=\s*([A-Za-z]+)')


class BudgetExhausted(Exception):
    """The launch budget ran out before the search finished."""


# --- the pack -----------------------------------------------------------------------------------------------------

@dataclass
class Pack:
    jars: list        # every jar of the mixed manifest
    subjects: list    # the sweep's subjects (popular, random): the jars that must come out OK
    candidates: list  # what ddmin may take out: the subjects, and any jar no subject needs
    closure: dict     # jar -> the jars it needs, from closure.json
    digests: dict     # jar -> SHA-256


def load_pack(manifest, data):
    """The pack a mixed manifest describes, its jars checked against the manifest's digests."""
    data = Path(data)
    rows = json.loads(Path(manifest).read_text(encoding='utf-8'))
    jars = [row['filename'] for row in rows]
    if len(set(jars)) != len(jars):
        raise SystemExit('The manifest names a jar twice')
    subjects = [row['filename'] for row in rows if row['kind'] in mixed.SUBJECT_KINDS]
    every = json.loads((data / 'closure.json').read_text(encoding='utf-8'))
    unknown = [jar for jar in subjects if jar not in every]
    if unknown:
        raise SystemExit('closure.json has no entry for ' + ', '.join(unknown))
    closure = {jar: list(every.get(jar, ())) for jar in jars}
    outside = sorted({dep for deps in closure.values() for dep in deps} - set(jars))
    if outside:
        raise SystemExit('closure.json needs jars the manifest does not hold: ' + ', '.join(outside))
    needed = {dep for jar in subjects for dep in closure[jar]}
    # A jar that is neither a subject nor anybody's dependency would otherwise be in the reference run and in no
    # configuration after it.
    candidates = subjects + [jar for jar in jars if jar not in subjects and jar not in needed]
    by_name = {row['filename']: row for row in rows}
    for jar in jars:
        problem = mixed.manifest_mismatch(by_name[jar], data / 'mods' / jar)
        if problem:
            raise SystemExit(f'Test input {jar}: {problem}')
    return Pack(jars, subjects, candidates, closure, {jar: mixed.sha256(data / 'mods' / jar) for jar in jars})


# --- one session's evidence ---------------------------------------------------------------------------------------

def _read(path):
    return path.read_text(encoding='utf-8', errors='replace') if path.is_file() else None


def game_exit(driver):
    """The game's own exit code as run-client-test.py prints it ('client exit=78'), or None when it never exited."""
    found = _EXIT.findall(driver or '')
    return int(found[-1]) if found else None


def outcome_signature(signature, result):
    """signature, or, when no exception named the failure (EXIT:<code>), the session's outcome alongside it.

    Without the outcome a stall, a missing world and a DEGRADED mod would all be EXIT:0 or EXIT:None and so one
    failure.
    """
    if not signature.startswith('EXIT:'):
        return signature
    parts = [result['run'], signature]
    bad = sorted(f"{row.get('modId')}={row.get('status')}" for row in result.get('bad_mods') or [])
    if bad:
        parts.append('bad:' + ','.join(bad))
    if result['run'] == 'PASS' and not result.get('saved'):
        parts.append('unsaved')
    return ' '.join(parts)


def winners(report_text, mods_text):
    """The arbitration a session ran with.

    rows: mod id -> [ecosystem, jar, version] of the copy compatibility-report.json lists, for every mod loaded;
    picks: mod id -> loader for each id forbric-mods.txt lists (only ids installed more than once). Either is None
    when the session left no such file. merge-report.txt says the same in the system language, so it is evidence to
    read, not something to compare.
    """
    rows = None
    try:
        report = json.loads(report_text) if report_text else None
    except ValueError:
        report = None
    if isinstance(report, dict) and report.get('mods'):
        rows = {row['modId']: [row.get('ecosystem'), row.get('jar'), row.get('version')]
                for row in report['mods'] if row.get('modId')}
    picks = None
    if mods_text is not None:
        picks = {}
        for raw in mods_text.splitlines():
            line = raw.strip()
            # The kernel writes every choice commented out ('# sodium = neoforge'); a player's pin has no '#'.
            line = (line[1:] if line.startswith('#') else line).split('#', 1)[0].strip()
            match = _PICK.fullmatch(line)
            if match:
                picks[match.group(1)] = match.group(2).lower()
    return dict(rows=rows, picks=picks)


def winner_differences(reference, observed):
    """How a session's arbitration differs from the reference's; empty when it ran the same copies."""
    differences = []
    if reference.get('rows') is not None:
        if observed.get('rows') is None:
            return ['no compatibility-report.json to compare the loaded copies with']
        for mod_id, copy in sorted(observed['rows'].items()):
            before = reference['rows'].get(mod_id)
            if before is None:
                differences.append(f'{mod_id}: {"/".join(map(str, copy))}, which the reference did not load')
            elif before != copy:
                differences.append(f'{mod_id}: {"/".join(map(str, before))} -> {"/".join(map(str, copy))}')
    if reference.get('picks') and observed.get('picks'):
        for mod_id in sorted(set(reference['picks']) & set(observed['picks'])):
            if reference['picks'][mod_id] != observed['picks'][mod_id]:
                differences.append(f'{mod_id} = {reference["picks"][mod_id]} -> {observed["picks"][mod_id]}')
    return differences


def observe(evidence, result, jars):
    """What the minimiser keeps of one session: verdict inputs, arbitration, and the seeds its evidence names."""
    evidence = Path(evidence)
    driver = _read(evidence / 'driver.log') or ''
    console = _read(evidence / 'client-console.log') or ''
    crashes = mixed.crash_reports(evidence)
    crash = _read(crashes[0]) if crashes else ''
    analysis = _read(evidence / mixed.CRASH_ANALYSIS) or ''
    report = _read(evidence / 'compatibility-report.json') or ''
    exit_code = game_exit(driver)
    signature = outcome_signature(ddmin_core.signature(console, crash, report, exit_code), result)
    return dict(strict=bool(result['strict']), run=result['run'], exit=exit_code, signature=signature,
                winners=winners(report, _read(evidence / 'forbric-mods.txt')),
                seeds=ddmin_core.seeds(crash, analysis, report, jars), seconds=result.get('seconds'))


def judge(reference, observation):
    """(verdict, winner differences) of a session against the reference session."""
    differences = winner_differences(reference['winners'], observation['winners'])
    if differences:
        return UNRESOLVED, differences
    return ddmin_core.outcome(reference['signature'], observation['signature'], passed=observation['strict']), []


# --- sessions, remembered -----------------------------------------------------------------------------------------

class Sessions:
    """Runs configurations through launch(jars, jvm, label) -> (evidence dir, mixed result), each at most once.

    cache.jsonl keeps every finished session keyed by the kernel's SHA-256, the sorted SHA-256 of the jars, the JVM
    flags and the world ticks, so a second invocation (or another round) launches only what it has not seen. A cache
    written under another kernel is refused, and so is a kernel or a jar that changes while this runs: either would
    file an outcome under a key that no longer describes what ran.
    """

    def __init__(self, out, launch, fingerprint, digests, ticks, budget, mods):
        self.out, self.launch, self.fingerprint, self.digests = Path(out), launch, fingerprint, digests
        self.mods = Path(mods)
        self.ticks, self.budget = ticks, budget
        self.kernel = fingerprint()
        self.cache_path = self.out / 'cache.jsonl'
        self.entries = {}
        self.launches = self.hits = 0
        if self.cache_path.is_file():
            for line in self.cache_path.read_text(encoding='utf-8').splitlines():
                if not line.strip():
                    continue
                entry = json.loads(line)
                if entry.get('kernel_sha256') != self.kernel:
                    raise SystemExit(f'{self.cache_path} holds sessions of kernel {entry.get("kernel_sha256")}, but the '
                                     f'installed kernel is {self.kernel}; choose a fresh --out')
                self.entries[entry['key']] = entry

    def key(self, jars, jvm):
        payload = dict(kernel_sha256=self.kernel, jar_sha256=sorted(self.digests[jar] for jar in jars),
                       jvm=list(jvm), ticks=self.ticks)
        return hashlib.sha256(json.dumps(payload, sort_keys=True).encode('utf-8')).hexdigest()

    def _label(self, key):
        runs = self.out / 'runs'
        taken = [int(path.name.split('-', 1)[0]) for path in runs.iterdir()
                 if path.name.split('-', 1)[0].isdigit()] if runs.is_dir() else []
        return f'{max(taken, default=0) + 1:03d}-{key[:10]}'

    def run(self, jars, jvm):
        """(cache entry, whether it came from the cache) for one configuration."""
        jars = sorted(jars)
        key = self.key(jars, jvm)
        if key in self.entries:
            self.hits += 1
            return self.entries[key], True
        if self.launches >= self.budget:
            raise BudgetExhausted()
        if self.fingerprint() != self.kernel:
            raise SystemExit('The installed kernel changed since this minimisation started; its sessions are void')
        label = self._label(key)
        self.launches += 1
        evidence, result = self.launch(jars, list(jvm), label)
        if self.fingerprint() != self.kernel:
            raise SystemExit(f'The installed kernel changed during session {label}; its outcome is void')
        changed = [jar for jar in jars if mixed.sha256(self.mods / jar) != self.digests[jar]]
        if changed:
            raise SystemExit(f'Test input changed during session {label}: ' + ', '.join(changed))
        entry = dict(key=key, kernel_sha256=self.kernel, jars=jars, jar_sha256={jar: self.digests[jar] for jar in jars},
                     jvm=list(jvm), ticks=self.ticks, label=label, observation=observe(evidence, result, jars))
        self.out.mkdir(parents=True, exist_ok=True)
        with self.cache_path.open('a', encoding='utf-8') as cache:
            cache.write(json.dumps(entry, sort_keys=True) + '\n')
        self.entries[key] = entry
        return entry, False


class Judged:
    """An oracle over one fixed reference: each call runs a configuration and records it with its verdict."""

    def __init__(self, sessions, reference, configure):
        self.sessions, self.reference, self.configure = sessions, reference, configure
        self.runs, self.failing = [], []

    def __call__(self, selection):
        jars, jvm = self.configure(list(selection))
        entry, cached = self.sessions.run(jars, jvm)
        verdict, differences = judge(self.reference, entry['observation'])
        row = dict(label=entry['label'], jars=entry['jars'], jvm=entry['jvm'], verdict=verdict,
                   signature=entry['observation']['signature'], cached=cached)
        if differences:
            row['winner_differences'] = differences
        self.runs.append(row)
        if verdict == FAIL:
            self.failing.append(list(selection))
        return verdict

    def smallest_failing(self):
        return min(self.failing, key=len) if self.failing else None


# --- narrowing to mixin configs and classes -----------------------------------------------------------------------

def _json(archive, name):
    try:
        value = json.loads(archive.read(name))
    except ValueError:
        return {}
    return value if isinstance(value, dict) else {}


def _nested(archive):
    """The jars nested in an archive that a loader opens: fabric.mod.json's jars and NeoForge's jarjar metadata."""
    names = set(archive.namelist())
    children = []
    if 'fabric.mod.json' in names:
        children += [row['file'] for row in _json(archive, 'fabric.mod.json').get('jars') or []
                     if isinstance(row, dict) and row.get('file')]
    if 'META-INF/jarjar/metadata.json' in names:
        children += [row['path'] for row in _json(archive, 'META-INF/jarjar/metadata.json').get('jars') or []
                     if isinstance(row, dict) and row.get('path')]
    return sorted(set(children) & names)


def _visit(data, visit, depth=0):
    """visit(archive) for a jar and for every jar nested in it."""
    if depth > 8:
        raise ValueError('nested jar depth exceeded')
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        visit(archive)
