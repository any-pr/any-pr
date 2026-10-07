#!/usr/bin/env python3
"""Run one of run/compat/win's drivers on macOS against the INSTALLED profile, the way a launcher would.

    mac-run.py <driver.py> [driver args...]

The Windows drivers resolve the installed version JSON themselves; only four things are Windows-specific and are
patched here, nothing else: the "launching requires Windows" guard, OS rules (osx instead of windows, so the
natives jars and -XstartOnFirstThread are chosen as a Mac launcher chooses them), child drivers being re-entered
through this wrapper, and process-tree cleanup (a POSIX terminate of the driver must also take its java child).
"""
import os
import platform
import re
import runpy
import signal
import subprocess
import sys
import time
from pathlib import Path

WIN = Path(__file__).resolve().parents[3] / 'run' / 'compat' / 'win'
sys.path.insert(0, str(WIN))
import common  # noqa: E402

def config(args, argument_parser):
    # common.config verbatim minus its final "launching requires Windows" guard.
    if not args.mc or not args.version:
        argument_parser.error('set FORBRIC_MC and FORBRIC_VERSION, or --mc and --version')
    mc = Path(args.mc).absolute()
    instance = Path(args.instance).absolute() if args.instance else mc / 'versions' / args.version
    return dict(mc=str(mc), version=args.version, instance=str(instance), world=args.world,
                java=args.java, version_json=str(mc / 'versions' / args.version / (args.version + '.json')),
                pid_file=str(instance / '.forbric-sweep.pid'), server_dir=str(instance / 'server-gen'),
                screenshots=str(instance / 'screenshots'), jvm=args.jvm,
                screenshot_flag='-Dforbric.clientSmokeScreenshots=100')


def rules_allow(entry):
    if not entry.get('rules'):
        return True
    allowed = False
    architecture = 'arm64' if platform.machine().lower() in ('arm64', 'aarch64') else 'x86_64'
    for rule in entry['rules']:
        if any(value for value in rule.get('features', {}).values()):
            continue
        spec = rule.get('os', {})
        if spec.get('name', 'osx') != 'osx':
            continue
        if 'arch' in spec and not re.fullmatch(spec['arch'], architecture):
            continue
        allowed = rule['action'] == 'allow'
    return allowed


def driver_command(configuration, filename):
    command = [sys.executable, str(Path(__file__).resolve()), filename, '--mc', configuration['mc'],
               '--version', configuration['version'], '--instance', configuration['instance'],
               '--world', configuration['world']]
    if configuration['java']:
        command += ['--java', configuration['java']]
    command += ['--jvm=' + value for value in configuration['jvm']]
    return command


def spawn(configuration, command, **kwargs):
    # Only a child DRIVER gets its own process group (so finish() can take the driver and its java together);
    # the java a driver starts stays in that driver's group, or a killpg of the driver would orphan it.
    if any(str(part).endswith('mac-run.py') for part in command):
        kwargs.setdefault('start_new_session', True)
    command = [str(part).replace('-Dforbric.clientSmokeDisconnectTicks=200',
               '-Dforbric.clientSmokeDisconnectTicks=' + os.environ.get('SWEEP_WORLD_TICKS', '200')) for part in command]
    process = subprocess.Popen(command, **kwargs)
    common.record_pid(configuration, process.pid)
    return process


def finish(configuration, process):
    # A driver started with its own session leads a process group that also holds its java. SIGTERM kills the
    # python driver at once, but a wedged game's shutdown hook can keep the JVM alive forever, so the group gets a
    # SIGKILL after the grace whether or not the driver itself is already gone.
    if process.poll() is None and any(str(part).startswith('@') for part in process.args):
        java = Path(str(process.args[0]))
        jcmd = java.with_name('jcmd')
        if jcmd.exists():
            try:
                stack = subprocess.run([str(jcmd), str(process.pid), 'Thread.print', '-l'],
                                       capture_output=True, text=True, timeout=15)
                Path(configuration['instance'], 'thread-dump-' + str(process.pid) + '.txt').write_text(stack.stdout + stack.stderr)
            except (OSError, subprocess.TimeoutExpired):
                pass
    grouped = os.getpgid(process.pid) == process.pid if process.poll() is None else True
    if process.poll() is None:
        try:
            os.killpg(process.pid, signal.SIGTERM) if grouped else process.terminate()
        except (ProcessLookupError, PermissionError):
            process.terminate()
        try:
            process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)
    if grouped:
        for _ in range(20):
            try:
                os.killpg(process.pid, 0)
            except (ProcessLookupError, PermissionError):
                break
            time.sleep(1)
        else:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except (ProcessLookupError, PermissionError):
                pass
    common.record_pid(configuration, process.pid, remove=True)


common.config, common.rules_allow, common.driver_command = config, rules_allow, driver_command
common.spawn, common.finish = spawn, finish

if __name__ == '__main__':
    script = WIN / sys.argv[1]
    sys.argv = [str(script)] + sys.argv[2:]
    runpy.run_path(str(script), run_name='__main__')
