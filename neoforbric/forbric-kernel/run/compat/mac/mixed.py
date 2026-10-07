"""Combine the sweep's subjects with their dependencies, then save and reload the complete pack.

    mixed.py [--subjects strict|all]

strict (the default) combines every subject that passed its own run strictly; the individual sweep must be complete,
from this kernel, over unchanged inputs. all combines every subject in manifest.json without reading any individual
result: each jar's bytes are checked against the digest the manifest records instead.

Importing this file starts nothing and reads no environment; ddmin.py calls run() for each pack it tests.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import sweep_verdict
from world_save import saved_since

TOOLS = Path(__file__).resolve().parent
SUBJECT_KINDS = ('popular', 'random')
# Copied whatever their age: the caller deletes the two reports before a second session, and forbric-mods.txt is the
# instance's arbitration record, written once on its first boot.
EVIDENCE = ['client-console.log', '.forbric-kernel/compatibility-report.json', '.forbric-kernel/load-report.txt',
            'forbric-mods.txt']
# Copied only when this session wrote them; nothing deletes the older ones.
MTIME_SLACK = 2.0
FRESH = ['screenshots/*.png', 'crash-reports/*.txt', 'thread-dump-*.txt', '.forbric-kernel/crash-analysis.txt',
         '.forbric-kernel/merge-report.txt']
CRASH_ANALYSIS = 'crash-analysis.txt'
_permod = None


def permod():
    """per-mod.py, loaded on first use: loading it reads PERMOD_INSTANCE and the installed profile."""
    global _permod
    if _permod is None:
        spec = importlib.util.spec_from_file_location('permod', TOOLS / 'per-mod.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        _permod = module
    return _permod


def drive(instance, log, ticks, jvm, stall, timeout, grace):
    """One run of the client driver against the installed profile, its output to log; the driver's exit code."""
    installed = permod()
    env = dict(os.environ, SWEEP_WORLD_TICKS=str(ticks), FORBRIC_MC=str(installed.MC), FORBRIC_INSTANCE=str(instance),
               FORBRIC_VERSION=installed.VERSION, FORBRIC_JAVA=installed.JAVA, CLIENT_STALL=str(stall),
               RUN_TIMEOUT=str(timeout), GRACE=str(grace))
    with log.open('w') as output:
        return subprocess.run([sys.executable, str(TOOLS / 'mac-run.py'), 'run-client-test.py',
                               '--jvm=-Dforbric.compatibilityPolicy=strict'] + ['--jvm=' + flag for flag in jvm],
                              env=env, stdout=output, stderr=subprocess.STDOUT).returncode


def crash_reports(evidence):
    """The crash reports among a session's evidence; crash-analysis.txt matches the same glob and is not one."""
    return sorted(path for path in Path(evidence).glob('crash-*.txt') if path.name != CRASH_ANALYSIS)


def run(label, ticks, subjects, out, jvm=(), stall=420, timeout=900, grace=30, instance=None, launch=None):
    """One client session of the prepared instance, judged as a pack; evidence and result.json go to out/label.

    subjects are the jars that must come out OK; jvm flags follow the strict compatibility policy; stall, timeout and
    grace are the driver's CLIENT_STALL, RUN_TIMEOUT and GRACE. instance defaults to per-mod.py's; launch, by default
    drive(), is what runs the driver, with drive()'s parameters.
    """
    instance = Path(instance) if instance is not None else permod().INST
    launch = launch or drive
    evidence = Path(out) / label
    evidence.mkdir(parents=True, exist_ok=False)
    start = time.time()
    # Files written during the session are told from leftovers by their mtime. A filesystem stamps them from a clock
    # that can trail time.time(): Windows' tick lags by up to ~16 ms, so a crash report written in the session's first
    # instant read as older than the session and was dropped, and the session read as clean. The instance is cleared
    # before every session, so a leftover is never seconds young; a little slack costs nothing.
    fresh = start - MTIME_SLACK
    returncode = launch(instance, evidence / 'driver.log', ticks, list(jvm), stall, timeout, grace)
    for filename in EVIDENCE:
        source = instance / filename
        if source.exists():
            shutil.copy2(source, evidence / source.name)
    for pattern in FRESH:
        for source in instance.glob(pattern):
            if source.stat().st_mtime >= fresh:
                shutil.copy2(source, evidence / source.name)
    driver = (evidence / 'driver.log').read_text(errors='replace')
    console = (evidence / 'client-console.log').read_text(errors='replace') if (evidence / 'client-console.log').exists() else ''
    report = json.loads((evidence / 'compatibility-report.json').read_text()) if (evidence / 'compatibility-report.json').exists() else {}
    crashes = crash_reports(evidence)
    verdict = sweep_verdict.classify_run(driver, console, crashes)
    bad = sweep_verdict.bad_rows(report)
    missing = sweep_verdict.missing_subjects(subjects, report)
    world = instance / 'saves' / 'compat-world'
    saved = saved_since(world, fresh)
    strict = sweep_verdict.pack_strict(returncode, verdict, report, missing, saved)
    result = dict(run=verdict, strict=strict, bad_mods=bad, missing_subjects=missing, saved=saved, world_ticks=ticks, seconds=int(time.time() - start))
    (evidence / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(label, result, flush=True)
    return result


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def manifest_mismatch(row, path):
    """Why a jar's bytes are not the manifest's, or None. pick.py records Modrinth's sha1 and size; a sha256 wins."""
    if row is None:
        return 'not in the manifest'
    data = Path(path).read_bytes()
    if row.get('sha256'):
        return None if hashlib.sha256(data).hexdigest() == row['sha256'] else 'sha256 differs from the manifest'
    if row.get('sha1'):
        if hashlib.sha1(data).hexdigest() != row['sha1']:
            return 'sha1 differs from the manifest'
        if 'size' in row and len(data) != row['size']:
            return 'size differs from the manifest'
        return None
    return 'the manifest records no digest for it'


def select_pack(data, mode, kernel, results):
    """(subjects, jars, sha256 of each jar, manifest rows of those jars) for the pack, or SystemExit saying why not.

    results is per-mod's results.jsonl, read only in strict mode.
    """
    data = Path(data)
    manifest = json.loads((data / 'manifest.json').read_text())
    all_subjects = {row['filename'] for row in manifest if row['kind'] in SUBJECT_KINDS}
    closure = json.loads((data / 'closure.json').read_text())
    if mode == 'strict':
        latest = {}
        for line in Path(results).read_text().splitlines():
            row = json.loads(line)
            latest[row['jar']] = row
        if all_subjects - latest.keys():
            raise SystemExit('Individual sweep incomplete; do not omit pending subjects')
        for name in all_subjects:
            if latest[name].get('kernel_sha256') != kernel:
                raise SystemExit('Individual result is from a different kernel: ' + name)
        subjects = sorted(name for name in all_subjects if latest[name].get('strict'))
    elif mode == 'all':
        subjects = sorted(all_subjects)
    else:
        raise ValueError('subjects mode is strict or all, not ' + repr(mode))
    selected = sorted(set(subjects) | {dep for name in subjects for dep in closure[name]})
    if not selected:
        raise SystemExit('No strictly passing subjects to combine' if mode == 'strict' else 'The manifest names no subjects')
    inputs = {name: sha256(data / 'mods' / name) for name in selected}
    if mode == 'strict':
        for name in subjects:
            for filename, fingerprint in latest[name]['input_sha256'].items():
                if (inputs.get(filename) or sha256(data / 'mods' / filename)) != fingerprint:
                    raise SystemExit('Test input changed: ' + filename)
    else:
        rows = {row['filename']: row for row in manifest}
        for name in selected:
            problem = manifest_mismatch(rows.get(name), data / 'mods' / name)
            if problem:
                raise SystemExit(f'Test input {name}: {problem}')
    return subjects, selected, inputs, [row for row in manifest if row['filename'] in selected]


def prepare_pack(jars):
    """A fresh per-mod instance holding exactly jars, the zero-mod world and options that keep the world running."""
    installed = permod()
    installed.prepare(jars)
    # Other test windows must not pause this five-minute mixed world when they take focus.
    options = installed.INST / 'options.txt'
    options.write_text(options.read_text().replace('pauseOnLostFocus:true', 'pauseOnLostFocus:false'))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--subjects', choices=('strict', 'all'), default='strict',
                        help='strict: the subjects that passed alone (default); all: every subject in manifest.json')
    args = parser.parse_args(argv)
    if not os.environ.get('PERMOD_DATA'):
        raise SystemExit('Set PERMOD_DATA to the sweep data directory')
    data = Path(os.environ['PERMOD_DATA']).resolve()
    os.environ['PERMOD_INSTANCE'] = str(data / os.environ.get('PERMOD_MIXED_INSTANCE', 'mixed-inst'))
    installed = permod()
    out = data / os.environ.get('PERMOD_MIXED_OUT', 'mixed')
    sha = installed.kernel_fingerprint()
    subjects, selected, inputs, rows = select_pack(data, args.subjects, sha,
                                                   data / os.environ.get('PERMOD_OUT', 'per-mod') / 'results.jsonl')
    out.mkdir(parents=True, exist_ok=False)
    (out / 'manifest.json').write_text(json.dumps(rows, indent=2) + '\n')
    prepare_pack(selected)
    first = run('first-load', 6000, subjects, out)
    second = dict(run='NOT_RUN', strict=False)
    if first['strict']:
        for filename in ['compatibility-report.json', 'load-report.txt']:
            (installed.INST / '.forbric-kernel' / filename).unlink(missing_ok=True)
        second = run('reload', 200, subjects, out)
    if installed.kernel_fingerprint() != sha:
        raise SystemExit('Kernel changed during mixed test')
    if {name: sha256(data / 'mods' / name) for name in selected} != inputs:
        raise SystemExit('Test input changed during mixed test')
    result = dict(kernel_sha256=sha, subjects_mode=args.subjects, subjects=subjects, jars=selected, input_sha256=inputs,
                  first=first, reload=second, strict=first['strict'] and second['strict'])
    (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    return 0 if result['strict'] else 1


if __name__ == '__main__':
    sys.exit(main())
