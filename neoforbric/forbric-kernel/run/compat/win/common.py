#!/usr/bin/env python3
"""Shared portable configuration and owned-process handling for the Windows drivers."""
import argparse
import base64
from contextlib import contextmanager
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import time
import zipfile


def parser(description):
    result = argparse.ArgumentParser(description=description)
    result.add_argument('--mc', default=os.environ.get('FORBRIC_MC'), help='Minecraft installation root; FORBRIC_MC')
    result.add_argument('--version', default=os.environ.get('FORBRIC_VERSION'), help='Installed version id; FORBRIC_VERSION')
    result.add_argument('--instance', default=os.environ.get('FORBRIC_INSTANCE'), help='Default: MC/versions/VERSION')
    result.add_argument('--world', default=os.environ.get('FORBRIC_WORLD', 'compat-world'))
    result.add_argument('--java', default=os.environ.get('FORBRIC_JAVA'))
    result.add_argument('--jvm', action='append', default=[], help='Extra JVM argument; use --jvm=-Dkey=value')
    result.add_argument('--print-config', action='store_true', help='Print configuration without files, processes or Windows APIs')
    return result


def config(args, argument_parser):
    if not args.mc or not args.version:
        argument_parser.error('set FORBRIC_MC and FORBRIC_VERSION, or --mc and --version')
    if Path(args.version).name != args.version or re.search(r'[<>:"/\\|?*]', args.version):
        argument_parser.error('version must be a single directory name')
    if args.world in ('.', '..') or re.search(r'[<>:"/\\|?*]', args.world):
        argument_parser.error('world must be a single directory name')
    mc = Path(args.mc).absolute()
    instance = Path(args.instance).absolute() if args.instance else mc / 'versions' / args.version
    result = dict(mc=str(mc), version=args.version, instance=str(instance), world=args.world,
                  java=args.java, version_json=str(mc / 'versions' / args.version / (args.version + '.json')),
                  pid_file=str(instance / '.forbric-sweep.pid'), server_dir=str(instance / 'server-gen'),
                  screenshots=str(instance / 'screenshots'), jvm=args.jvm,
                  screenshot_flag='-Dforbric.clientSmokeScreenshots=100')
    if args.print_config:
        print(json.dumps(result, indent=2))
        return None
    if os.name != 'nt':
        argument_parser.error('launching requires Windows; --print-config works on any platform')
    return result


def safe_filename(name):
    name = re.sub(r'[<>:"/\\|?*\x00-\x1f]', '_', name).rstrip(' .')
    if not name or name in ('.', '..'):
        raise ValueError('empty filename')
    if name.split('.')[0].upper() in {'CON', 'PRN', 'AUX', 'NUL', *(f'COM{i}' for i in range(1, 10)), *(f'LPT{i}' for i in range(1, 10))}:
        name = '_' + name
    return name


@contextmanager
def pid_lock(configuration):
    path = Path(configuration['pid_file'])
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.with_suffix('.pid.lock').open('a+b') as lock:
        if lock.tell() == 0:
            lock.write(b'0')
            lock.flush()
        lock.seek(0)
        if os.name == 'nt':
            import msvcrt
            msvcrt.locking(lock.fileno(), msvcrt.LK_LOCK, 1)
        else:
            import fcntl
            fcntl.flock(lock, fcntl.LOCK_EX)
        try:
            yield path
        finally:
            lock.seek(0)
            if os.name == 'nt':
                msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(lock, fcntl.LOCK_UN)


def record_pid(configuration, pid, remove=False):
    with pid_lock(configuration) as path:
        values = path.read_text(encoding='ascii').splitlines() if path.exists() else []
        values = [value for value in values if value != str(pid)]
        if not remove:
            values.append(str(pid))
        temporary = path.with_suffix('.pid.tmp')
        temporary.write_text(''.join(value + '\n' for value in values), encoding='ascii')
        temporary.replace(path)


@contextmanager
def own_driver(configuration):
    record_pid(configuration, os.getpid())
    try:
        yield
    finally:
        record_pid(configuration, os.getpid(), remove=True)


# A mod's first-run screen joins vanilla's initial-screen chain (Gui.buildInitialScreens), and vanilla runs quick-play
# only as that chain's LAST link — so a first launch sits on the mod's welcome screen until a player clicks through,
# on a native loader exactly as on Forbric. sweep90-win-r6 drew wover-ui's BetterX welcome for five minutes and never
# joined the world. A sweep plays the player who has already dismissed it: each row is the mod's own "seen" flag,
# merged into its config before the client starts; every other setting stays the mod's default.
FIRST_RUN_SEEN = (
    ('config/wover/client.json', ('internal', 'did_present_welcome_screen'), True),
)


def acknowledge_first_run(instance, rows=FIRST_RUN_SEEN):
    for relative, keys, value in rows:
        target = Path(instance) / relative
        try:
            data = json.loads(target.read_text(encoding='utf-8')) if target.is_file() else {}
        except ValueError:
            data = {}
        if not isinstance(data, dict):
            data = {}
        node = data
        for key in keys[:-1]:
            if not isinstance(node.get(key), dict):
                node[key] = {}
            node = node[key]
        node[keys[-1]] = value
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(data, indent=2), encoding='utf-8')


# A sweep's client plays one language whoever runs it. options.txt stays the player's, but its `lang:` line decides
# which assets every mod loads, so it is an input to the verdict like the jars and the seed — and the only one nobody
# pinned: every Mac gate and local sweep instance plays en_us, the Windows profile plays its player's zh_cn.
# sweep90-win-r7c died at world join on that alone. Axiom 6.1.3 builds its Dear ImGui font atlas on the first frame
# it draws, for zh_cn from two Noto CJK fonts (7.7 + 11.5 MB) read into byte[]s it does not keep. Its bundled
# imgui-java (imgui.moulberry92, Dear ImGui 1.92.7) pins each array only for the add call, while since 1.92 the atlas
# keeps that pointer and reads it again at build(); a GC in between moves or frees the array, stb_truetype reads a
# garbage cmap format (imstb_truetype.h:1590) and the binding's assert handler calls System.exit(1). The CJK reads
# are humongous allocations, which start exactly such a GC once a full pack has filled the heap. It is not Forbric's:
# native Fabric 0.19.5 with only Axiom and fabric-api asserts on the same line in zh_cn once a GC lands between the
# add and the build — forced, or under -XX:+UseSerialGC -Xmn16m; under default G1 that minimal native pack did not
# crash in the runs recorded. en_us narrows the race rather than closing it — four fonts, ~570 KB, no humongous read:
# under that same young generation native Fabric does not assert, and this pack joins and leaves cleanly on Forbric.
SWEEP_LANGUAGE = 'en_us'
# `--lang player` pins nothing and plays whatever the player's options.txt names. It is how the player's real language,
# and every mod's assets for it, goes back under test on purpose — a zh_cn run with Axiom left out, say.
PLAYER_LANGUAGE = 'player'
# Minecraft reads options.txt top to bottom into one map, so when a file somehow holds two lang lines the last wins.
LANG_LINE = re.compile(rb'^lang:([^\r\n]*)', re.M)
# The lang line is not all a run changes: the client rewrites the whole file itself. Minecraft.<init> sets
# startedCleanly to false and saves, and only onGameLoadFinished saves it true again — a client killed while it loads
# leaves false, and the player's next start resets their fullscreen mode. So for as long as a sweep's block runs, this
# record keeps the player's file as it was (`original`, base64; `absent` when there was none), what the sweep changed
# in it (`lang` it wrote, None under `player` and when there was no file; `was`, the line it replaced), and who wrote
# it (`pid` and `started`).
# It outlives the block only when its writer dies before its own restore:
#   - push-and-run's stop kills the driver along with the client. Before its kill it notes that the record's writer is
#     alive; after the kill it puts `original` back whole, as the writer's own finally would have
#     (push-and-run.py `stop_command`, whose Python twin is note_sweep_writer / restore_after_stop).
#   - anything else — a reboot, the driver dying on its own — leaves it for whoever comes next, the stop or a client or
#     bisect driver, and they find a writer that is gone. By then the player may have played and changed settings, so
#     only the lang line goes back, and only while it still names the sweep's language (restore_player_language).
# The writer is a pid together with its process's start time, because after a reboot Windows gives the pid to some
# other process. lang and was are the line's bytes read as Latin-1, so both sides compare the same bytes.
SWEEP_RECORD = 'options.txt.forbric-sweep'
# PowerShell's (Get-Process).StartTime.ToUniversalTime().Ticks counts from 0001-01-01; Windows' FILETIME, which
# GetProcessTimes gives, from 1601-01-01. This is the gap, in the same 100 ns ticks (.NET's DateTime.FileTimeOffset).
DOTNET_TICKS_AT_1601 = 504911232000000000


def client_language(value):
    """--lang / --client-lang: a Minecraft language code, or `player`. Empty — an exported but blank FORBRIC_LANG — is
    the default, as an unset one is, rather than an error that stops the client driver before it starts."""
    value = (value or '').strip() or SWEEP_LANGUAGE
    if value != PLAYER_LANGUAGE and not re.fullmatch(r'[a-z0-9_]{2,16}', value):
        raise argparse.ArgumentTypeError(f'not a Minecraft language code or {PLAYER_LANGUAGE!r}: {value!r}')
    return value


def language_argument(argument_parser):
    argument_parser.add_argument(
        '--lang', type=client_language, default=os.environ.get('FORBRIC_LANG', ''),
        help=f"language the client plays; default {SWEEP_LANGUAGE}, also for an empty FORBRIC_LANG; "
             f"'{PLAYER_LANGUAGE}' plays the player's options.txt as it is. Either way the player's options.txt is put "
             f"back as it was afterwards (after a reboot mid-run, only its lang line)")


def describe_language(code, player, absent=False):
    """The driver's `client language ...` line, which push-and-run quotes in report.md: what the client played, and
    what the player's own file names."""
    if absent:
        played = SWEEP_LANGUAGE if code == PLAYER_LANGUAGE else code
        return f"{played}; the player has no options.txt (vanilla plays en_us), and the one the client writes is removed after the run"
    if code == PLAYER_LANGUAGE:
        return f"{player or 'en_us'}; the player's options.txt, played as it is and restored after the run"
    named = player or 'no language (vanilla plays en_us)'
    return f"{code}; the player's options.txt names {named}, restored after the run"


def replace_bytes(path, data):
    # Whole or not at all: a sweep killed mid-write must not leave the player half an options.txt.
    temporary = path.with_name(path.name + '.forbric-tmp')
    temporary.write_bytes(data)
    temporary.replace(path)


def process_started(pid):
    """When the running process `pid` started, as PowerShell's (Get-Process -Id pid).StartTime.ToUniversalTime().Ticks
    reads it — the identity observe_command in push-and-run.py uses too — or None when no such process runs.

    Off Windows, where only the tests run, a process's start time is not at hand; a live one reads 0."""
    if os.name != 'nt':
        try:
            os.kill(pid, 0)
        except (ProcessLookupError, PermissionError):
            return None
        return 0
    import ctypes
    from ctypes import wintypes
    kernel32 = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel32.OpenProcess.argtypes = (wintypes.DWORD, wintypes.BOOL, wintypes.DWORD)
    kernel32.OpenProcess.restype = wintypes.HANDLE
    kernel32.GetExitCodeProcess.argtypes = (wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD))
    kernel32.GetExitCodeProcess.restype = wintypes.BOOL
    kernel32.GetProcessTimes.argtypes = (wintypes.HANDLE,) + (ctypes.POINTER(wintypes.FILETIME),) * 4
    kernel32.GetProcessTimes.restype = wintypes.BOOL
    kernel32.CloseHandle.argtypes = (wintypes.HANDLE,)
    kernel32.CloseHandle.restype = wintypes.BOOL
    # PROCESS_QUERY_LIMITED_INFORMATION. It fails for a pid nobody holds, and for another user's process, which a
    # sweep's driver never is: the stop and the drivers run as the same account.
    handle = kernel32.OpenProcess(0x1000, False, pid)
    if not handle:
        return None
    try:
        code, times = wintypes.DWORD(), [wintypes.FILETIME() for _ in range(4)]
        if not kernel32.GetExitCodeProcess(handle, ctypes.byref(code)) or code.value != 259:  # STILL_ACTIVE
            return None
        if not kernel32.GetProcessTimes(handle, *(ctypes.byref(value) for value in times)):
            raise ctypes.WinError(ctypes.get_last_error())
        return (times[0].dwHighDateTime << 32 | times[0].dwLowDateTime) + DOTNET_TICKS_AT_1601
    finally:
        kernel32.CloseHandle(handle)


def read_sweep_record(path):
    """The record at `path`. One that cannot be read is acted on by nobody — not a driver, not the stop — so it raises
    ValueError naming the file to delete once a person has looked at options.txt."""
    try:
        record, problem = json.loads(path.read_bytes().decode('ascii')), None
    except ValueError as error:
        record, problem = None, str(error)
    def text(value):
        return value is None or isinstance(value, str)
    def count(value):
        return isinstance(value, int) and not isinstance(value, bool) and value >= 0
    def base64_text(value):
        try:
            base64.b64decode(value, validate=True)
            return isinstance(value, str)
        except (TypeError, ValueError):
            return False
    if (isinstance(record, dict) and {'lang', 'was', 'absent', 'pid', 'started', 'original'} <= record.keys()
            and text(record['lang']) and text(record['was']) and isinstance(record['absent'], bool)
            and count(record['pid']) and count(record['started'])
            and (record['absent'] or base64_text(record['original']))):
        return record
    raise ValueError(f"{path} is not a sweep record, so nothing acted on it and options.txt was left as it is; check "
                     f"options.txt (its lang: line above all), then delete {path}: {problem or repr(record)}")


def sweep_writer_alive(record):
    started = process_started(record['pid'])
    return started is not None and started == record['started']


def writer_identity(record):
    return f"{record['pid']}:{record['started']}"


def undo_sweep(options, record, whole):
    """Put options.txt back from `record`, and say what was done.

    `whole`: the block ended with its writer — the stop killed it — and nobody else wrote options.txt in it, the same
    assumption the writer's own finally rests on, so the player's bytes go back whole, over the client's rewrite too;
    when the player had no options.txt, the one the client wrote goes. Otherwise only the lang line goes back to the
    player's, and only while every lang line still names what the sweep wrote: a player who has picked a language
    since keeps it, and every other line stays as the file now has it, as does a file where the player had none."""
    if whole:
        if record['absent']:
            if options.is_file():
                options.unlink()
                return 'removed the options.txt its client wrote; the player had none'
            return 'the player had no options.txt, and none is left'
        replace_bytes(options, base64.b64decode(record['original']))
        return "put the player's file back byte for byte"
    wrote, was = record['lang'], record['was']
