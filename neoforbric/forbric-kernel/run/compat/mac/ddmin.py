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
        for child in _nested(archive):
            _visit(archive.read(child), visit, depth + 1)


def _declared(archive):
    """(config, environment) for each mixin config one archive declares, in any of the three loaders' ways."""
    names = set(archive.namelist())
    declared = []
    if 'fabric.mod.json' in names:
        for entry in _json(archive, 'fabric.mod.json').get('mixins') or []:
            if isinstance(entry, str):
                declared.append((entry, '*'))
            elif isinstance(entry, dict) and entry.get('config'):
                declared.append((entry['config'], entry.get('environment', '*')))
    for filename in ('META-INF/mods.toml', 'META-INF/neoforge.mods.toml'):
        if filename in names:
            try:
                metadata = tomllib.loads(archive.read(filename).decode('utf-8'))
            except (ValueError, UnicodeDecodeError):
                metadata = {}
            declared += [(row['config'], '*') for row in metadata.get('mixins') or []
                         if isinstance(row, dict) and row.get('config')]
    if 'META-INF/MANIFEST.MF' in names:
        for line in archive.read('META-INF/MANIFEST.MF').decode('utf-8', 'replace').splitlines():
            if line.startswith('MixinConfigs:'):
                declared += [(name.strip(), '*') for name in line.split(':', 1)[1].split(',') if name.strip()]
    return declared


def mixin_configs(jar_bytes):
    """config name -> its mixin entries that a CLIENT applies, over every jar (and nested jar) in jar_bytes.

    A config is named as its mod declares it (fabric.mod.json, [[mixins]] in either mods.toml, a manifest's
    MixinConfigs), which is the name -Dforbric.disableMixinConfigs matches; an entry is written as its config lists
    it, the form -Dforbric.suppressMixins takes after 'config:'. The config file may sit in a nested jar, as
    sodium-neoforge's do. A config declared for the server only, and a config's server list, are left out.
    """
    jar_bytes = list(jar_bytes)
    declared = []
    for data in jar_bytes:
        _visit(data, lambda archive: declared.extend(_declared(archive)))
    wanted = [name for name, environment in declared if environment != 'server']
    files = {}

    def read(archive):
        for name in set(wanted) & set(archive.namelist()):
            files.setdefault(name, archive.read(name))
    for data in jar_bytes:
        _visit(data, read)
    configs = {}
    for name in wanted:
        entries = configs.setdefault(name, [])
        try:
            config = json.loads(files[name]) if name in files else {}
        except ValueError:
            config = {}
        for section in ('mixins', 'client'):
            for entry in config.get(section) or []:
                if isinstance(entry, str) and entry not in entries:
                    entries.append(entry)
    return configs


def narrow(sessions, reference, jars, jvm, configs):
    """The mixin configs, then the mixin classes within them, the failure of the closed set jars needs.

    Both are ddmin over what stays ENABLED: everything else goes into -Dforbric.disableMixinConfigs or
    -Dforbric.suppressMixins. ddmin never tries nothing, so that is tried first: a failure that survives with every
    config off needs none of them.
    """
    names = sorted(configs)

    def configuration(enabled, kept=None):
        flags = list(jvm)
        disabled = [name for name in names if name not in enabled]
        if disabled:
            flags.append(f'-D{DISABLE_CONFIGS}=' + ','.join(disabled))
        if kept is not None:
            suppressed = [entry for entry in entries if entry not in kept]
            if suppressed:
                flags.append(f'-D{SUPPRESS_MIXINS}=' + ','.join(suppressed))
        return jars, flags

    result = dict(configs=None, classes=None)
    entries = []
    by_config = Judged(sessions, reference, lambda enabled: configuration(set(enabled)))
    result['config_runs'] = by_config.runs
    if not names:
        result.update(configs=[], classes=[], note='the closed set declares no mixin config')
        return result
    if by_config([]) == FAIL:
        result.update(configs=[], classes=[], note='the failure happens with every one of these mixin configs disabled')
        return result
    enabled = ddmin_core.ddmin(names, by_config)
    result['configs'] = enabled
    entries = [f'{name}:{entry}' for name in enabled for entry in configs[name]]
    by_class = Judged(sessions, reference, lambda kept: configuration(set(enabled), set(kept)))
    result['class_runs'] = by_class.runs
    if not entries:
        result.update(classes=[], note='the configs it needs list no mixin class')
    elif by_class([]) == FAIL:
        result.update(classes=[], note='the failure needs these configs but none of their mixin classes')
    else:
        result['classes'] = ddmin_core.ddmin(entries, by_class)
    return result


# --- rounds -------------------------------------------------------------------------------------------------------

def minimise_round(sessions, pack, candidates, jvm, mods, use_seeds=True, narrowing=False):
    """Run closed(candidates) as the reference, then ddmin the candidates against it (and narrow the result)."""
    closure = pack.closure
    try:
        reference_entry, _ = sessions.run(ddmin_core.closed(candidates, closure), jvm)
    except BudgetExhausted:
        return dict(candidates=len(candidates), reference=None, status='BUDGET')
    reference = reference_entry['observation']
    rows = (reference['winners'].get('rows') or {})
    picks = reference['winners'].get('picks') or {}
    record = dict(candidates=len(candidates), reference=dict(
        label=reference_entry['label'], jars=len(reference_entry['jars']), run=reference['run'],
        strict=reference['strict'], signature=reference['signature'],
        arbitration={mod_id: dict(loader=loader, copy=rows.get(mod_id)) for mod_id, loader in sorted(picks.items())}))
    if reference['strict']:
        record['status'] = 'PASSED'
        return record
    seed_jars = [jar for jar in reference['seeds'] if jar in set(candidates)] if use_seeds else []
    record['seeds'] = seed_jars
    # minimise hands the oracle configurations it has already closed over their dependencies.
    oracle = Judged(sessions, reference, lambda config: (config, list(jvm)))
    record['runs'] = oracle.runs
    try:
        reduction = ddmin_core.minimise(candidates, oracle, closure, seed_jars)
    except BudgetExhausted:
        best = oracle.smallest_failing()
        record.update(status='BUDGET', smallest_failing_closed=best,
                      smallest_failing=[jar for jar in best if jar in set(candidates)] if best else None)
        return record
    except NotReproduced as refused:
        record.update(status='NOT_REPRODUCED', outcome=refused.outcome)
        return record
    record.update(status='MINIMISED', minimal=reduction.minimal, closed=reduction.closed, seeded=reduction.seeded)
    if narrowing:
        configs = mixin_configs((Path(mods) / jar).read_bytes() for jar in reduction.closed)
        try:
            record['narrow'] = narrow(sessions, reference, reduction.closed, jvm, configs)
        except BudgetExhausted:
            record['narrow'] = dict(status='BUDGET')
    return record


def minimise_pack(pack, out, launch, fingerprint, mods, ticks=200, jvm=(), budget=80, use_seeds=True, iterate=False,
                  narrowing=False, settings=None):
    """Every round, written to <out>/ddmin-result.json; returns that result.

    With iterate, each MINIMISED round's minimal set leaves the candidates and the rest is run again as the next
    round's reference, until a round's reference passes, the candidates run out, or a round does not finish.
    """
    jvm = list(jvm)
    if narrowing and any(flag.startswith((f'-D{DISABLE_CONFIGS}=', f'-D{SUPPRESS_MIXINS}=')) for flag in jvm):
        raise SystemExit(f'--narrow sets -D{DISABLE_CONFIGS} and -D{SUPPRESS_MIXINS} itself; leave them out of --jvm')
    if not pack.candidates:
        raise SystemExit('The manifest names no jar to minimise')
    out = Path(out)
    sessions = Sessions(out, launch, fingerprint, pack.digests, ticks, budget, mods)
    rounds, candidates = [], list(pack.candidates)
    while candidates:
        record = minimise_round(sessions, pack, candidates, jvm, mods, use_seeds, narrowing)
        rounds.append(record)
        if record['status'] != 'MINIMISED' or not iterate:
            break
        candidates = [jar for jar in candidates if jar not in record['minimal']]
    changed = sorted(jar for jar in pack.jars if mixed.sha256(Path(mods) / jar) != pack.digests[jar])
    if changed:
        raise SystemExit('Test input changed during the minimisation: ' + ', '.join(changed))
    result = dict(status=rounds[-1]['status'], kernel_sha256=sessions.kernel, jvm=jvm, ticks=ticks, budget=budget,
                  launches=sessions.launches, cache_hits=sessions.hits, rounds=rounds, **(settings or {}))
    out.mkdir(parents=True, exist_ok=True)
    (out / 'ddmin-result.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--manifest', required=True, help="the failing pack: a mixed run's manifest.json")
    parser.add_argument('--out', help='parent of ddmin/ (default: PERMOD_DATA)')
    parser.add_argument('--ticks', type=int, default=200, help='world ticks before the clean disconnect (default 200)')
    parser.add_argument('--stall', type=int, default=120, help="driver's CLIENT_STALL in seconds (default 120)")
    parser.add_argument('--timeout', type=int, default=420, help="driver's RUN_TIMEOUT in seconds (default 420)")
    parser.add_argument('--grace', type=int, default=20, help="driver's GRACE in seconds (default 20)")
    parser.add_argument('--jvm', action='append', default=[], help='an extra JVM flag for every session: --jvm=-Da=b')
    parser.add_argument('--budget', type=int, default=80, help='most game launches in this invocation (default 80)')
    parser.add_argument('--no-seeds', action='store_true', help="do not start from the reference's own suspects")
    parser.add_argument('--iterate', action='store_true', help='take each minimal set out and minimise what fails next')
    parser.add_argument('--narrow', action='store_true', help='narrow each minimal set to mixin configs, then classes')
    args = parser.parse_args(argv)
    if not os.environ.get('PERMOD_DATA'):
        raise SystemExit('Set PERMOD_DATA to the sweep data directory (mods/ and closure.json)')
    data = Path(os.environ['PERMOD_DATA']).resolve()
    os.environ['PERMOD_INSTANCE'] = str(data / os.environ.get('PERMOD_DDMIN_INSTANCE', 'ddmin-inst'))
    installed = mixed.permod()
    pack = load_pack(args.manifest, data)
    out = Path(args.out).resolve() / 'ddmin' if args.out else data / 'ddmin'
    runs, subjects = out / 'runs', set(pack.subjects)

    def launch(jars, jvm, label):
        mixed.prepare_pack(jars)
        result = mixed.run(label, args.ticks, [jar for jar in jars if jar in subjects], runs, jvm=jvm,
                           stall=args.stall, timeout=args.timeout, grace=args.grace)
        return runs / label, result

    manifest = Path(args.manifest).resolve()
    settings = dict(manifest=str(manifest), manifest_sha256=mixed.sha256(manifest),
                    closure_sha256=mixed.sha256(data / 'closure.json'), stall=args.stall, timeout=args.timeout,
                    grace=args.grace, seeds=not args.no_seeds, iterate=args.iterate, narrow=args.narrow)
    result = minimise_pack(pack, out, launch, installed.kernel_fingerprint, data / 'mods', ticks=args.ticks,
                           jvm=args.jvm, budget=args.budget, use_seeds=not args.no_seeds, iterate=args.iterate,
                           narrowing=args.narrow, settings=settings)
    for number, record in enumerate(result['rounds'], 1):
        print(f"round {number}: {record['status']} {record.get('minimal', '')} "
              f"reference={(record['reference'] or {}).get('signature')}")
    print(f"{result['launches']} launches, {result['cache_hits']} from the cache; {out / 'ddmin-result.json'}")
    return 0 if result['status'] in ('MINIMISED', 'PASSED') else 1


if __name__ == '__main__':
    sys.exit(main())
