#!/usr/bin/env python3
"""Test every jar on its own (plus only the jars it requires) with the INSTALLED Forbric profile, one at a time.

    per-mod.py [--only a.jar,b.jar] [--redo]

Each run uses a fresh isolated game directory, a copied zero-mod world and baseline options.
mods = the jar + closure.json[jar]. The client driver joins the world, screenshots at tick 100, disconnects at 200.
Results append to per-mod/results.jsonl; evidence per jar in per-mod/<jar>/.
  run:  PASS (joined, clean disconnect, exit 0, frame drawn) | CRASH | STALL | NO_WORLD | NOT_DRAWN | FAIL
  mod:  the tested jar's own status in compatibility-report.json: OK | DEGRADED | FAILED | ABSENT (kernel never listed it)
"""
import hashlib, json, os, shlex, shutil, subprocess, sys, time
from pathlib import Path
from sweep_verdict import classify_run, mod_status, subject_strict

HERE = Path(os.environ.get('PERMOD_DATA', str(Path(__file__).resolve().parents[3] / 'build' / 'sweep100-mac-network')))
TOOLS = Path(__file__).resolve().parent
FIXTURES = Path(__file__).resolve().parents[3] / 'build' / 'sweep80-mac'
MC = Path(os.environ.get('PERMOD_MC', str(Path.home() / 'Library/Application Support/minecraft')))
INST = Path(os.environ.get('PERMOD_INSTANCE', str(HERE / 'inst')))
DATA = Path(os.environ.get('PERMOD_DATA', str(HERE)))
OUT = HERE / os.environ.get('PERMOD_OUT', 'per-mod')
JAVA = os.environ.get('FORBRIC_JAVA') or shutil.which('java')
profiles = [p.stem for p in (MC / 'versions').glob('*/*.json') if json.loads(p.read_text()).get('mainClass') == 'net.forbric.kernel.boot.KernelClientLaunch']
VERSION = os.environ.get('FORBRIC_VERSION') or (profiles[0] if len(profiles) == 1 else None)
if not JAVA or not VERSION:
    raise SystemExit('Set FORBRIC_JAVA and FORBRIC_VERSION for the isolated installed profile')


def kernel_fingerprint():
    profile = json.loads((MC / 'versions' / VERSION / (VERSION + '.json')).read_text())
    entry = next(lib for lib in profile['libraries'] if lib['name'].startswith('net.forbric:forbric-kernel:'))
    return hashlib.sha256((MC / 'libraries' / entry['downloads']['artifact']['path']).read_bytes()).hexdigest()


def prepare(jars):
    if INST.resolve() == MC.resolve():
        raise RuntimeError('The disposable instance must differ from the installed Minecraft root')
    if INST.exists():
        if not (INST / '.forbric-sweep-instance').is_file():
            raise RuntimeError('Refusing to clear an unmarked directory: ' + str(INST))
        shutil.rmtree(INST)
    (INST / 'mods').mkdir(parents=True)
    (INST / '.forbric-sweep-instance').write_text('Disposable compatibility test instance\n')
    for j in jars:
        shutil.copy2(DATA / 'mods' / j, INST / 'mods' / j)
    shutil.copytree(FIXTURES / 'world-vanilla', INST / 'saves' / 'compat-world')
    shutil.copy2(FIXTURES / 'options-base.txt', INST / 'options.txt')


def test(jar, deps):
    before = kernel_fingerprint()
    inputs = {name: hashlib.sha256((DATA / 'mods' / name).read_bytes()).hexdigest() for name in [jar] + deps}
    prepare([jar] + deps)
    ev = OUT / jar
    if ev.exists():
        shutil.rmtree(ev)
    ev.mkdir(parents=True)
    env = dict(os.environ, FORBRIC_MC=str(MC), FORBRIC_VERSION=VERSION, FORBRIC_INSTANCE=str(INST),
               FORBRIC_JAVA=JAVA, CLIENT_STALL=os.environ.get('CLIENT_STALL', '120'), RUN_TIMEOUT=os.environ.get('RUN_TIMEOUT', '600'), GRACE=os.environ.get('GRACE', '20'))
    started = time.time()
    with (ev / 'driver.log').open('w') as log:
        subprocess.run([sys.executable, str(TOOLS / 'mac-run.py'), 'run-client-test.py',
                        '--jvm=-Dforbric.compatibilityPolicy=continue'] + ['--jvm=' + flag for flag in shlex.split(os.environ.get('SWEEP_JVM', ''))], env=env, stdout=log, stderr=subprocess.STDOUT)
    seconds = int(time.time() - started)
    for src, dst in [(INST / 'client-console.log', 'client-console.log'),
                     (INST / '.forbric-kernel/load-report.txt', 'load-report.txt'),
                     (INST / '.forbric-kernel/compatibility-report.json', 'compatibility-report.json')]:
        if src.exists():
            shutil.copy2(src, ev / dst)
    crashes = sorted((INST / 'crash-reports').glob('*.txt')) if (INST / 'crash-reports').exists() else []
    for c in crashes:
        shutil.copy2(c, ev / c.name)
    shots = sorted((INST / 'screenshots').glob('*.png')) if (INST / 'screenshots').exists() else []
    if shots:
        shutil.copy2(shots[-1], ev / 'screenshot.png')
    driver = (ev / 'driver.log').read_text(errors='replace')
    console = (ev / 'client-console.log').read_text(errors='replace') if (ev / 'client-console.log').exists() else ''
    report = json.loads((ev / 'compatibility-report.json').read_text()) if (ev / 'compatibility-report.json').exists() else {}
    run = classify_run(driver, console, crashes)
    status, detail = mod_status(jar, report)
    dependency_issues = [m['modId'] + '=' + m['status'] for m in report.get('mods', []) if m.get('status') != 'OK']
    missing = json.loads((DATA / 'closure-missing.json').read_text()) if (DATA / 'closure-missing.json').exists() else {}
    unresolved = {name: missing[name] for name in [jar] + deps if missing.get(name)}
    strict = subject_strict(run, status, report, unresolved)
    for stack in INST.glob('thread-dump-*.txt'):
        shutil.copy2(stack, ev / stack.name)
    if kernel_fingerprint() != before:
        raise RuntimeError('Installed kernel changed during the test; outcome is invalid')
    row = dict(kernel_sha256=before, input_sha256=inputs, jar=jar, deps=deps, run=run, mod=status, detail=detail, seconds=seconds,
               strict=strict, dependency_issues=dependency_issues, unresolved=unresolved, drew=('drew=True' in driver), crash=[c.name for c in crashes])
    with (OUT / 'results.jsonl').open('a') as f:
        f.write(json.dumps(row) + '\n')
    print(f"{run:14} {status:8} {seconds:4}s  {jar}  {' '.join(detail)[:160]}", flush=True)


def main():
    closure = json.loads((DATA / 'closure.json').read_text())
    OUT.mkdir(parents=True, exist_ok=True)
    done = set()
    if (OUT / 'results.jsonl').exists() and '--redo' not in sys.argv:
        previous = [json.loads(l) for l in (OUT / 'results.jsonl').read_text().splitlines() if l.strip()]
        fingerprint = kernel_fingerprint()
        if any(row.get('kernel_sha256') != fingerprint for row in previous):
            raise RuntimeError('Results belong to another candidate; choose a fresh PERMOD_OUT directory')
        done = {row['jar'] for row in previous}
    manifest = json.loads((DATA / 'manifest.json').read_text())
    jars = sorted(row['filename'] for row in manifest if row['kind'] in ('popular', 'random'))
    if '--only' in sys.argv:
        jars = sys.argv[sys.argv.index('--only') + 1].split(',')
    for i, jar in enumerate(jars, 1):
        if jar in done:
            continue
        print(f'[{i}/{len(jars)}]', end=' ', flush=True)
        test(jar, closure[jar])


if __name__ == '__main__':
    main()
