#!/usr/bin/env python3
"""M34: freeze a per-nonce client installation and independently validate real simulation evidence.

Does not build or mutate a source world. Only the gate wrapper builds, before the immutable snapshot.
A CONTROL_PASS is deliberately not a release soak. Reachable retired servers require review.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import signal
import subprocess
import sys
import time
import uuid
import zipfile
sys.dont_write_bytecode = True
from evidence import source_record

MIN_SECONDS = 7200
NATIVE_RETENTION = Path(__file__).with_name('native-retention.json')


def signal_owned_group(process, value):
    # The owned child may exit between poll() and signaling. Preserve its real exit status.
    try:
        os.killpg(process.pid, value)
    except ProcessLookupError:
        pass


def wait_for_client(process, log, timeout):
    """Keep a reported game crash from leaving an unresponsive test window until the soak timeout."""
    began = time.monotonic()
    crash_seen = None
    offset, tail = 0, ''
    while process.poll() is None:
        with Path(log).open(errors='replace') as stream:
            stream.seek(offset); tail = (tail + stream.read())[-8192:]; offset = stream.tell()
        if crash_seen is None and '#@!@# Game crashed!' in tail:
            crash_seen = time.monotonic()
            signal_owned_group(process, signal.SIGTERM)
        if (crash_seen is not None and time.monotonic() - crash_seen > 5) or time.monotonic() - began > timeout:
            signal_owned_group(process, signal.SIGKILL)
            process.wait()
            return process.returncode
        time.sleep(.5)
    return process.returncode


class RetentionReview(ValueError):
    """Activity can be proven while release acceptance remains refused."""
    def __init__(self, activity):
        super().__init__('retained old servers require evidence review; release acceptance remains refused')
        self.activity = activity


def validate_compatibility(path, started_ns):
    path = Path(path)
    if not path.is_file() or path.stat().st_mtime_ns < started_ns:
        raise ValueError('missing or stale final compatibility report')
    report = json.loads(path.read_text())
    required = [row for row in report['findings'] if row['confidence'] == 'CONFIRMED' and row['required']]
    if report['policy'] != 'STRICT' or report['confirmedRequired'] != len(required) or required:
        raise ValueError('final compatibility report is not strict with zero required losses')
    if any(row['status'] == 'FAILED' for row in report.get('catalogFailures', [])):
        raise ValueError('unclassified initialization failure in final compatibility report')
    return {'sha256': digest(path), 'policy': report['policy'], 'confirmedRequired': 0}


def digest(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()


def inventory(root):
    root = Path(root)
    if root.is_symlink():
        raise ValueError(f'symlinks are not permitted in copied inputs: {root}')
    result = {}
    for path in sorted(root.rglob('*')):
        if path.is_symlink():
            raise ValueError(f'symlinks are not permitted in copied inputs: {path}')
        if path.is_file():
            result[str(path.relative_to(root))] = digest(path)
    return result


def dump(path, value):
    Path(path).write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')


def copy_frozen(source, destination, records):
    source = Path(source)
    if source.is_symlink() or not source.is_file():
        raise ValueError(f'expected an actual input file: {source}')
    before = digest(source)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    if before != digest(source) or before != digest(destination):
        raise ValueError(f'input changed while being copied: {source}')
    destination.chmod(0o444)
    records.append({'source': str(source.resolve()), 'snapshot': str(destination.resolve()),
                    'sha256': before, 'size': destination.stat().st_size})
    return destination


def copy_tree(source, destination):
    before = inventory(source)
    shutil.copytree(source, destination)
    if inventory(source) != before or inventory(destination) != before:
        raise ValueError(f'input tree changed while being copied: {source}')
    return before


def native_retention_roots(frozen_mod_hashes, registry=NATIVE_RETENTION):
    """Reviewed roots whose exact mod jar is in this run. Nothing else may be cut by the controller."""
    entries = json.loads(Path(registry).read_text())['roots']
    present = set(frozen_mod_hashes)
    return [entry for entry in entries if entry['modJarSha256'] in present]


def verify_native_evidence(kernel, entry):
    """The registry is a claim; release acceptance re-reads the native reproduction it names."""
    path = Path(kernel) / entry['nativeEvidence']
    if not path.is_file():
        raise ValueError(f"native retention evidence missing for {entry['root']}: run {entry['reproduce']}")
    comparison = json.loads(path.read_text())
    arms = comparison.get('arms', [])
    if not comparison.get('sameModHashes') or {arm.get('engine') for arm in arms} != {'native', 'forbric'}:
        raise ValueError('native retention evidence does not compare identical mods on both loaders')
    for arm in arms:
        if not arm.get('nativeRetentionReproduced') or arm.get('proof', {}).get('root') != entry['root']:
            raise ValueError(f"native retention of {entry['root']} is not reproduced by arm {arm.get('engine')}")
        inputs = json.loads(Path(arm['inputs']['path']).read_text())
        if digest(arm['inputs']['path']) != arm['inputs']['sha256']:
            raise ValueError('native retention evidence inputs changed after the comparison')
        if entry['modJarSha256'] not in {mod['sha256'] for mod in inputs['modSet']}:
            raise ValueError(f"native retention evidence did not use the registered {entry['mod']}")
    return {'root': entry['root'], 'evidence': str(path), 'sha256': digest(path)}


def validate_native_release(result, roots, serial):
    """Accept retention only when the controller's post-measurement cut of reviewed roots freed every server."""
    before = result['oldServers']
    after = result.get('oldServersAfterNativeRelease', before)
    released = result.get('nativeRetentionRelease', [])
    if sorted(server['server'] for server in after) != list(range(1, serial + 1)):
        raise ValueError('post-release observations do not cover every completed session')
    if any(before_row['server'] == after_row['server'] and not before_row['alive'] and after_row['alive']
           for before_row in before for after_row in after):
        raise ValueError('a collected server reappeared after the native release')
    allowed = {entry['root']: entry for entry in roots}
    for row in released:
        if row.get('root') not in allowed:
            raise ValueError('controller cut a root that is not reviewed for this run: ' + str(row.get('root')))
    if not any(server['alive'] for server in before):
        if released:
            raise ValueError('native roots were cut although no server was retained')
        return []
    if any(server['alive'] for server in after):
        return None
    return [{'root': row['root'], 'removedStoppedServerEntries': row['removedStoppedServerEntries'],
             'modJarSha256': allowed[row['root']]['modJarSha256']} for row in released]


HEAP_PATHS = Path(__file__).with_name('HeapPaths.java')


def mod_owned_retention(java, dump, mods, game_jars, output):
    """Ask HeapPaths whether every strong path to each retained server runs through state a mod keeps."""
    command = [java, '-Xmx6g', str(HEAP_PATHS), str(dump), 'net.minecraft.client.server.IntegratedServer', '3',
               '--mod-owned', str(mods), *map(str, game_jars)]
    with Path(output).open('w') as log:
        code = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT).returncode
    verdicts = [line.split()[1:] for line in Path(output).read_text().splitlines() if line.startswith('VERDICT ')]
    return {'exitCode': code, 'reachable': sum(v[1] == 'REACHABLE' for v in verdicts),
            'unreachable': sum(v[1] == 'UNREACHABLE' for v in verdicts), 'analysis': str(output), 'sha256': digest(output)}


def validate_trace(rows, result, nonce, seconds, control, process_seconds, min_sessions=3, native_roots=(), mod_owned=None):
    if not control and seconds < MIN_SECONDS:
        raise ValueError('release acceptance requires at least 7200 seconds')
    if not rows or rows[0]['type'] != 'start' or rows[-1]['type'] != 'finish':
        raise ValueError('missing complete start/finish telemetry')
    if result['nonce'] != nonce or result['pid'] != rows[0]['pid']:
        raise ValueError('foreign or stale result')
    if rows[0]['requiredSeconds'] != seconds or rows[0]['releaseEligible'] != (not control):
        raise ValueError('controller configuration does not match launcher')
    pid, sequence, previous, serial, sessions = rows[0]['pid'], 0, None, 0, 0
    ticks = active = opens = 0
    visits, unloads, reloads = [0] * 6, [0] * 6, [0] * 6
    seen, loaded, unloaded = [False] * 6, [False] * 6, [False] * 6
    desired = None
    close_requested = False
    for row in rows:
        if row['nonce'] != nonce or row['pid'] != pid or row['sequence'] != sequence + 1:
            raise ValueError('noncontiguous telemetry or changed process identity')
        sequence += 1
        kind = row['type']
        if kind == 'open':
            if previous is not None or sessions != serial:
                raise ValueError('reopen before completed normal disconnect')
            opens += 1
        elif kind == 'join':
            if previous is not None or row['server'] != serial + 1 or not row['occupied']:
                raise ValueError('invalid integrated-server join')
            serial += 1
            previous = row
            seen, loaded, unloaded = [False] * 6, [False] * 6, [False] * 6
            desired = None
        elif kind == 'move':
            if previous is None or row['server'] != serial or desired is not None:
                raise ValueError('movement outside occupied session or preceding arrival missing')
            desired = row['point']
            if desired not in range(6):
                raise ValueError('unexpected chunk probe')
        elif kind == 'sample':
            if previous is None or row['server'] != serial:
                raise ValueError('sample outside joined server')
            dt = row['tick'] - previous['tick']
            dw = row['gameTime'] - previous['gameTime']
            dn = row['sampleNano'] - previous['sampleNano']
            if min(dt, dw, dn) < 0:
                raise ValueError('simulation counters regressed')
            actual = min(dt, dw)
            if row['occupied'] and previous['occupied'] and not row['paused'] and not previous['paused']:
                ticks += actual
                active += min(dn, actual * 50_000_000)
            if len(row['loaded']) != 6 or len(row['chunks']) != 3 or min(row['chunks']) < 0:
                raise ValueError('missing three-dimensional chunk measurements')
            for i, present in enumerate(row['loaded']):
                if present:
                    if unloaded[i] and not loaded[i]:
                        reloads[i] += 1
                    seen[i] = True
                elif seen[i] and loaded[i]:
                    unloaded[i] = True
                    unloads[i] += 1
                loaded[i] = present
            if desired is not None and row['point'] == desired and row['loaded'][desired]:
                visits[desired] += 1
                desired = None
            previous = row
        elif kind == 'save-and-disconnect':
            if previous is None or desired is not None:
                raise ValueError('normal disconnect before final probe arrived')
            close_requested = True
        elif kind == 'disconnect':
            if previous is None or not close_requested or row['server'] != serial or not row['stopped'] or not row['normalSaveRequested']:
                raise ValueError('disconnect lacks native stop/save evidence')
            sessions += 1
            previous, close_requested = None, False
    if previous is not None or sessions != serial or opens != serial - 1 or sessions < min_sessions:
        raise ValueError('insufficient same-JVM normal save and reopen cycles')
    expected = 'CONTROL_PASS' if control else 'RELEASE_PASS'
    if result['status'] not in (expected, 'REVIEW_REQUIRED'):
        raise ValueError('controller did not pass: ' + result['status'] + '; retained-server evidence is reviewable, not proof of a leak')
    if ticks != result['actualTicks'] or active != result['activeNanos']:
        raise ValueError('reported activity differs from independently counted simulation')
    if active < seconds * 1_000_000_000 or ticks < seconds * 20 or process_seconds < seconds:
        raise ValueError('insufficient real occupied simulation; idle wall time does not count')
    if any(n < 2 for n in visits) or any(n == 0 for n in unloads + reloads):
        raise ValueError('all six chunk probes must unload and reload observably')
    for name, measured in [('visits', visits), ('unloads', unloads), ('reloads', reloads)]:
        if measured != result[name]:
            raise ValueError('coverage counter mismatch: ' + name)
    if sorted(server['server'] for server in result['oldServers']) != list(range(1, serial + 1)):
        raise ValueError('retired-server observations do not cover every completed session')
    if any(not server['stopped'] for server in result['oldServers']):
        raise ValueError('a retired server was not stopped normally')
    activity = {'activityVerified': True, 'actualTicks': ticks,
            'activeSeconds': active / 1_000_000_000, 'sessions': sessions,
            'visits': visits, 'unloads': unloads, 'reloads': reloads}
    attributed = validate_native_release(result, native_roots, serial)
    if attributed is None:
        residual = sum(server['alive'] for server in result.get('oldServersAfterNativeRelease', result['oldServers']))
        # Residual servers pass only when every strong path to each of them runs through a field a mod keeps (none
        # through the game, a carrier or Forbric alone), and when most sessions' servers were collected, so that
        # what is held is a mod's last-value state and not a per-session accumulation.
        if (not mod_owned or mod_owned['exitCode'] != 0 or mod_owned['reachable'] != 0
                or mod_owned['unreachable'] != residual or residual * 2 >= sessions):
            raise RetentionReview(activity)
        activity['modOwnedRetention'] = {'retained': residual, 'sessions': sessions,
                                         'analysis': mod_owned['analysis'], 'sha256': mod_owned['sha256']}
        attributed = []
    if result['status'] == 'REVIEW_REQUIRED' and not any(server['alive'] for server in result['oldServers']):
        raise ValueError('controller requested retention review without a retained-server witness')
    if result['status'] != 'REVIEW_REQUIRED' and any(server['alive'] for server in
                                                    result.get('oldServersAfterNativeRelease', result['oldServers'])):
        raise ValueError('controller reported a pass while a retired server is still reachable')
    if attributed:
        activity['nativeRetentionAttributed'] = attributed
    return {'status': expected, 'releaseAccepted': not control, **activity}
