#!/usr/bin/env python3
"""Stage and observe one Windows compatibility run using the configured transports."""
import argparse
import datetime
import hashlib
import json
import ntpath
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import zipfile

HERE = Path(__file__).resolve().parent
KERNEL = HERE.parent.parent
STAGED = Path(os.environ.get('FORBRIC_OLD', KERNEL.parent / 'forbric-loader')) / 'run'
sys.path.insert(0, str(HERE / 'win'))
from common import LANG_LINE, SWEEP_RECORD, client_language, safe_filename

# Explicit children only. Never delete the instance directory or a launcher-owned file.
#
# replay_recordings is a previous run's output, never an input, and a killed client leaves its recording unfinished.
# At startup ReplayMod 2.6.27 (ReplayFilesService) moves recording/ into the replay folder and opens RestoreReplayGui
# over the title screen for every unfinished recording it finds, and quick-play waits behind that screen: in a Mac run
# behind sweep90-win-r7c's diagnosis it logged `Found partially saved replay, offering recovery` for an earlier run's
# recording as the title screen came up, and the world started loading only after `Attempting recovery`, 72 s later.
CLEAN = ('config', 'mods', 'saves', 'logs', '.forbric-kernel', '.mixin.out', '.fabric',
         'crash-reports', 'screenshots', 'server-gen', 'quickPlay', 'resourcepacks',
         'defaultconfigs', '.cache', '.physics_mod_cache', 'replay_recordings', 'client-console.log',
         'server-console.log', 'bisect-console.log')
ARTIFACTS = {
    'net.forbric:forbric-kernel': KERNEL / 'build/libs/forbric-kernel-0.1.0-SNAPSHOT.jar',
    'net.forbric:patched-mc-merged': STAGED / 'merged-base/patched-mc-merged-26.2.jar',
    'net.forbric:forge-runtime': (STAGED / 'merged-base/forge-runtime-interop.jar'
                                if (STAGED / 'merged-base/forge-runtime-interop.jar').is_file()
                                else STAGED / 'forge-runtime/forge-runtime.jar'),
    'net.forbric:neoforge-runtime': STAGED / 'neoforge-runtime/neoforge-runtime.jar',
}


def ps(value):
    return "'" + str(value).replace("'", "''") + "'"


def transport(function, *arguments):
    result = subprocess.run(['bash', '-c', '. "$1"; shift; "$@"', 'compat',
                             str(HERE / 'lib-compat.sh'), function, *map(str, arguments)],
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            timeout=2 * int(os.environ.get('COMPAT_CALL_TIMEOUT', '240')) + 10)
    if result.returncode:
        raise RuntimeError(f'{function} failed ({result.returncode}): {result.stdout}')
    return result.stdout.strip()


def remote(command):
    return transport('remote_ps', command)


def put(local, target):
    transport('remote_put', local, target)


def get(source, local):
    local = Path(local)
    local.parent.mkdir(parents=True, exist_ok=True)
    transport('remote_get', source, local)
    if not local.is_file():
        raise RuntimeError('download did not produce ' + str(local))


def stop_command(instance):
    # Only PIDs recorded by these drivers/gates, never a process-name kill. The recorded PIDs include the client and
    # bisect drivers themselves, so the kill also skips their own restore of the player's options.txt: the stop notes
    # before its kill whether the sweep that owns the file is alive, and does that restore itself once every killed
    # process has let go of the file (sweep_record_commands).
    files = [ntpath.join(instance, name) for name in ('.forbric-sweep.pid', '.forbric-gate.pid')]
    files.append(ntpath.join(instance, 'server-gen', '.forbric-gate.pid'))
    before, after = sweep_record_commands(instance)
    # The restore sits in a finally: a PID that refuses to die throws, and a writer killed before that throw would
    # otherwise keep Minecraft's own rewrite of the player's file. `after` never restores while the writer lives.
    return (before + "try { " +
            "foreach ($file in @(" + ','.join(map(ps, files)) + ")) { "
            "if (Test-Path -LiteralPath $file) { foreach ($line in (Get-Content -LiteralPath $file)) { "
            "if ($line -match '^\\d+$' -and [int]$line -gt 0) { "
            "$owned = Get-Process -Id ([int]$line) -ErrorAction SilentlyContinue; "
            "if ($owned) { & taskkill /T /F /PID $line | Out-Null; "
            "if ($LASTEXITCODE -ne 0 -and (Get-Process -Id ([int]$line) -ErrorAction SilentlyContinue)) "
            "{ throw ('could not stop recorded PID ' + $line) }; "
            "Wait-Process -Id ([int]$line) -Timeout 10 -ErrorAction SilentlyContinue } } }; "
            "Remove-Item -LiteralPath $file -Force } } } finally { " + after + " }")


def sweep_record_commands(instance):
    """The stop's part in win/common.py's SWEEP_RECORD, as PowerShell to run before its kill and after it.

    Before the kill it reads the record and notes whether the process that wrote it is alive: its pid together with
    its start time, the identity observe_command uses, so a pid Windows has handed on after a reboot is not taken for
    it. After the kill:
      - the writer was alive and this stop killed it: its own finally never ran, so the stop does what it would have,
        and the player's file goes back whole from the record, or the one the client wrote goes when the player had
        none. The lang line alone would leave Minecraft's own rewrite behind, startedCleanly:false above all.
      - the writer was already gone before the stop (a reboot): only the lang line goes back, and only while it still
        names the sweep's language, since the player may have changed settings since. A file the client created where
        the player had none stays.
      - the writer still runs: it is not one this stop killed, and its own finally restores the file.
    A record nobody can read is acted on by nobody: the stop kills first, then fails naming the file to delete.
    Python twin: note_sweep_writer / restore_after_stop, which the tests run; PowerShell cannot run here. Bytes are
    read as Latin-1, as the record's strings are, so every byte survives the round trip."""
    options, record, temporary = (ps(ntpath.join(instance, name))
                                  for name in ('options.txt', SWEEP_RECORD, 'options.txt.forbric-tmp'))
    lang_line = "'(?m)" + LANG_LINE.pattern.decode('ascii') + "'"
    path = ntpath.join(instance, SWEEP_RECORD)
    unreadable = ps(f'{path} is not a sweep record, so nothing acted on it and options.txt was left as it is; '
                    f'check options.txt (its lang: line above all), then delete {path}')
    # One step, like os.replace on the Python side: Move-Item -Force onto an existing file deletes it first, and a kill
    # in between would leave no options.txt. A plain $null reaches Replace as an empty backup name, which it refuses.
    # Replace needs a file to replace; one the player removed meanwhile gets a plain move.
    replace = (f"if (Test-Path -LiteralPath {options}) {{ [IO.File]::Replace({temporary}, {options}, [NullString]::Value) }} "
               f"else {{ [IO.File]::Move({temporary}, {options}) }}")
    functions = (
        "function Read-SweepRecord { try { "
        f"$r = Get-Content -Raw -LiteralPath {record} | ConvertFrom-Json; "
        "if (-not (@('lang', 'was', 'absent', 'pid', 'started', 'original') "
        "| Where-Object { $r.PSObject.Properties.Name -notcontains $_ }) "
        "-and ($null -eq $r.lang -or $r.lang -is [string]) -and ($null -eq $r.was -or $r.was -is [string]) "
        "-and $r.absent -is [bool] -and ($r.pid -is [int] -or $r.pid -is [long]) -and $r.pid -ge 0 "
        "-and ($r.started -is [int] -or $r.started -is [long]) -and $r.started -ge 0 "
        "-and ($r.absent -or $null -ne [Convert]::FromBase64String($r.original))) { return $r } } catch { }; "
        "return $null }; "
        "function Test-SweepWriter($r) { try { $p = Get-Process -Id ([int]$r.pid) -ErrorAction SilentlyContinue; "
        "return [bool]($p -and $p.StartTime.ToUniversalTime().Ticks -eq [long]$r.started) } catch { return $false } }; ")
    before = (functions +
              "$sweepWriter = $null; $sweepUnreadable = $false; "
              f"if (Test-Path -LiteralPath {record}) {{ $r = Read-SweepRecord; "
              "if ($null -eq $r) { $sweepUnreadable = $true } "
              "elseif (Test-SweepWriter $r) { $sweepWriter = [string]$r.pid + ':' + [string]$r.started; "
              "$sweepWriterPid = [int]$r.pid } }; ")
    # The kill loop waits only for the PIDs it finds alive, and taskkill /T has already ended the driver as a child of
    # its job: wait for the writer itself, so a driver still being torn down is not taken for one this stop missed.
    after = (f"if ($sweepUnreadable) {{ throw {unreadable} }}; "
             "if ($sweepWriter) { Wait-Process -Id $sweepWriterPid -Timeout 10 -ErrorAction SilentlyContinue }; "
             f"if (Test-Path -LiteralPath {record}) {{ $r = Read-SweepRecord; "
             f"if ($null -eq $r) {{ throw {unreadable} }}; "
             "if (-not (Test-SweepWriter $r)) { "
             "if ($sweepWriter -eq ([string]$r.pid + ':' + [string]$r.started)) { "
             f"if ($r.absent) {{ if (Test-Path -LiteralPath {options}) {{ Remove-Item -LiteralPath {options} -Force }} }} "
             f"else {{ [IO.File]::WriteAllBytes({temporary}, [Convert]::FromBase64String($r.original)); {replace} }} }} "
             f"elseif (-not $r.absent -and $null -ne $r.lang -and (Test-Path -LiteralPath {options})) {{ "
             "$latin1 = [Text.Encoding]::GetEncoding(28591); "
             f"$text = $latin1.GetString([IO.File]::ReadAllBytes({options})); "
             f"$langs = @([regex]::Matches($text, {lang_line}) | ForEach-Object {{ $_.Groups[1].Value }}); "
             "if ($langs.Count -gt 0 -and @($langs | Where-Object { $_ -cne $r.lang }).Count -eq 0) { "
             "if ($null -eq $r.was) { $text = [regex]::Replace($text, '(?m)^lang:[^\\r\\n]*(\\r?\\n)?', '') } "
             "else { $text = [regex]::Replace($text, '(?m)^lang:[^\\r\\n]*', ('lang:' + $r.was).Replace('$', '$$')) }; "
             f"[IO.File]::WriteAllBytes({temporary}, $latin1.GetBytes($text)); {replace} }} }}; "
             f"Remove-Item -LiteralPath {record} -Force -ErrorAction SilentlyContinue }} }}")
    return before, after


def clean_command(instance):
    return '; '.join("if (Test-Path -LiteralPath " + ps(ntpath.join(instance, child)) +
                     ') { Remove-Item -LiteralPath ' + ps(ntpath.join(instance, child)) +
                     ' -Force -Recurse }' for child in CLEAN)


def resolve_artifacts(profile, overrides):
    result = dict(ARTIFACTS)
    for item in overrides:
        coordinate, local = item.split('=', 1)
        if coordinate not in result:
            raise ValueError('unsupported artifact: ' + coordinate)
        result[coordinate] = Path(local).resolve()
    entries = {}
    for entry in profile.get('libraries', []):
        coordinate = ':'.join(entry['name'].split(':')[:2])
        if coordinate in result:
            path = entry.get('downloads', {}).get('artifact', {}).get('path')
            if not path or ntpath.isabs(path) or '..' in Path(path.replace('\\', '/')).parts:
                raise ValueError('unsafe or missing library path: ' + coordinate)
            if coordinate in entries:
                raise ValueError('duplicate artifact: ' + coordinate)
            entries[coordinate] = (result[coordinate], path)
    if entries.keys() != result.keys():
        raise ValueError('profile is missing artifacts: ' + ', '.join(result.keys() - entries.keys()))
    return entries


def mod_files(directory):
    files, seen = [], set()
    for jar in sorted(directory.glob('*.jar')):
        name = safe_filename(jar.name)
        if name.casefold() in seen:
            raise ValueError('sanitized filename collision: ' + name)
        seen.add(name.casefold())
        files.append((jar, name))
    if not files:
        raise ValueError('mod directory has no jars: ' + str(directory))
    return files


# The long process owns its status file. Polling never starts a replacement job. It also owns its two log files:
# Start-Process with -RedirectStandard* creates the child with inherited handles, so the job held the remote shell's
# own output pipe and the start command could not return until the game exited — past the shell server's 300 s
# limit for every client run. Started without redirection, nothing of the shell is inherited.
JOB = '''import json, os, pathlib, subprocess, sys, time, traceback
from common import own_driver
status, instance, out_log, err_log, *command = sys.argv[1:]
sys.stdout = open(out_log, 'w', encoding='utf-8', buffering=1)
sys.stderr = open(err_log, 'w', encoding='utf-8', buffering=1)
path = pathlib.Path(status)
started_ns = time.time_ns()
def publish(state, **fields):
    temp = path.with_suffix('.tmp')
    temp.write_text(json.dumps(dict(state=state, pid=os.getpid(), started_ns=started_ns, **fields)), encoding='utf-8')
    temp.replace(path)
with own_driver(dict(pid_file=str(pathlib.Path(instance) / '.forbric-sweep.pid'))):
    publish('running')
    try:
        result = subprocess.run([sys.executable, *command], stdin=subprocess.DEVNULL, stdout=sys.stdout, stderr=sys.stderr)
        publish('done', returncode=result.returncode)
    except BaseException:
        publish('done', returncode=2, error=traceback.format_exc())
'''


class NotOurJob(RuntimeError):
    """The status file was published by a process this run did not start.

    Never transport flakiness, so it is never retried. The one way it happens is a launcher shim: the command
    named by --python re-executes a different interpreter, the driver publishes THAT process's pid, and the pid
    this run is allowed to stop belongs to a wrapper that has already exited. Retrying it burned a whole 97-jar
    sweep and reported a server test that had returned 0 as a FAIL.
    """


def observe_command(pid, start_ticks, status):
    # A status file alone is never evidence that a job is still alive. StartTime also rejects PID reuse.
    return (f'$process = Get-Process -Id {pid} -ErrorAction SilentlyContinue; '
            f'$alive = $null -ne $process -and $process.StartTime.ToUniversalTime().Ticks -eq {start_ticks}; '
            '$result = $null; '
            f'if (Test-Path -LiteralPath {ps(status)}) {{ $result = Get-Content -Raw -LiteralPath {ps(status)} | ConvertFrom-Json }}; '
            '@{alive=[bool]$alive; result=$result} | ConvertTo-Json -Depth 8 -Compress')


def run_job(args, stage, remote_tools, output, driver, extra=()):
    run_dir = ntpath.join(args.instance, '.forbric-compat', args.label)
    status = ntpath.join(run_dir, stage + '-status.json')
    command = [ntpath.join(remote_tools, 'job.py'), status, args.instance,
               ntpath.join(run_dir, stage + '.log'), ntpath.join(run_dir, stage + '-stderr.log'),
               ntpath.join(remote_tools, driver), '--mc', args.mc, '--version', args.version,
               '--instance', args.instance, '--world', args.world]
    if getattr(args, 'java', None):
        command += ['--java', args.java]
    command += ['--jvm=' + value for value in getattr(args, 'jvm', [])]
    command += list(extra)
    argument_line = subprocess.list2cmdline(command)
    start = (f'Remove-Item -LiteralPath {ps(status)} -Force -ErrorAction SilentlyContinue; '
             f'$job = Start-Process -FilePath {ps(args.python)} -ArgumentList {ps(argument_line)} '
             f'-WorkingDirectory {ps(remote_tools)} -PassThru -WindowStyle Hidden; '
             "Write-Output ('FORBRIC_PID=' + $job.Id); "
             "Write-Output ('FORBRIC_STARTED=' + $job.StartTime.ToUniversalTime().Ticks)")
    handle = dict(status=status, driver=driver)
    result_file = output / (stage + '-result.json')
    try:
        result = remote(start)
        match = re.search(r'^FORBRIC_PID=(\d+)$', result, re.M)
        birth = re.search(r'^FORBRIC_STARTED=(\d+)$', result, re.M)
        if match:
            handle['pid'] = int(match[1])
        if birth:
            handle['start_ticks'] = int(birth[1])
        (output / (stage + '-handle.json')).write_text(json.dumps(handle, indent=2))
        if not match or not birth:
            raise RuntimeError('Start-Process returned no verifiable process handle: ' + result)
        pid, start_ticks = handle['pid'], handle['start_ticks']
        print(f'{stage}: started owned job {pid}', flush=True)
        deadline, misses = time.monotonic() + args.timeout, 0
        while time.monotonic() < deadline:
            try:
                observation = json.loads(remote(observe_command(pid, start_ticks, status)))
                if not isinstance(observation.get('alive'), bool):
                    raise ValueError('process liveness missing from observation')
                state = observation.get('result')
                if state is not None and not isinstance(state, dict):
                    raise ValueError('status is not an object: ' + str(state))
                if state is not None and state.get('pid') != pid:
                    raise NotOurJob(
                        f'{args.python} started process {pid}, but the job published pid {state.get("pid")} — '
                        'that command is a launcher shim, not an interpreter, so this run owns a wrapper it '
                        'cannot stop and cannot vouch for the job that did the work. Point FORBRIC_PYTHON at '
                        'the real interpreter (a Python Manager shim names it in <command>.__target__) and run '
                        f'again; the job itself published {state}')
                misses = 0
            except NotOurJob:
                raise
            except (RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
                misses += 1
