#!/usr/bin/env python3
"""Portable source-development entry point; Python standard library only."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
from functools import lru_cache
import json
import os
from pathlib import Path
import platform
import re
import shutil
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
KERNEL = ROOT / 'forbric-kernel'
STATE = KERNEL / '.dev'
MC_VERSION = '26.2'
API_PINS = (
    ('fabric-api-0.155.2+26.2.jar',
     'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.155.2+26.2/fabric-api-0.155.2+26.2.jar',
     'd6518c770024cbe8a556248f16fcdbb91c6a62f50227a6c3bae8190511e2c1b8'),
    ('energy-5.0.0.jar', 'https://maven.modmuss50.me/teamreborn/energy/5.0.0/energy-5.0.0.jar',
     '889afc438d3e4add5cfdac76517da7987a2c495e4731690a56f2c5dee775db59'),
)
STAGED_FILES = ('neoforge-base/patched-mc-neoforge-26.2.jar', 'neoforge-runtime/neoforge-runtime.jar',
                # Not launched, but the bytecode tests read the patched side the base was staged from.
                'neoforge-patched/patched-mc-neoforge-26.2.jar',
                # Also only read by tests: the pins that tie the NeoForge side to this base,
                # and the canary mod (see CANARIES).
                'neoforge-patched/patched-mc-neoforge-26.2.jar.pins',
                'neoforge-runtime/forbricneolive.jar')
# forbric-loader/run/build-testmods.sh's canary mod, built the same way into the stage: (sources, jar, javac
# --release, the staged game jars it compiles against). Its sources go beside it, as in forbric-loader/run/,
# because the tests check the packaged data against the source it was built from.
CANARY_SOURCES = ROOT / 'forbric-loader' / 'run'
CANARIES = (
    ('livemod-src-neoforge', 'neoforge-runtime/forbricneolive.jar', '21',
     ('neoforge-runtime/neoforge-runtime.jar', 'neoforge-patched/patched-mc-neoforge-26.2.jar')),
)
# What build-testmods.sh packages besides classes.
CANARY_RESOURCES = ('META-INF', 'forbriclive.mixins.json', 'data', 'assets')
CONSOLE_PINS = (
    ('jline-reader', '26333a275de502adf1dd9e6ea50aa0b4021412c71490df9ed5e88a648886ee89'),
    ('jline-terminal', 'c0f5d70901255da66a94e59778b265d19f9308342578e34c88fc92d1b0c65fef'),
    ('jline-terminal-jna', '58ca9d719c373206af15775ee3cd5f268136ea0d0c4e009c3e96a6d4612d5c66'),
)


@lru_cache(maxsize=1)
def download_context():
    context = ssl.create_default_context()
    # python.org macOS installs can lack the optional certificate bundle; use the OS PEM trust store.
    if not ssl.get_default_verify_paths().cafile and Path('/etc/ssl/cert.pem').is_file():
        context.load_verify_locations('/etc/ssl/cert.pem')
    return context


def system_name():
    return {'Darwin': 'osx', 'Windows': 'windows', 'Linux': 'linux'}[platform.system()]


def default_minecraft_dir(system=None, env=None, home=None):
    system, env, home = system or system_name(), os.environ if env is None else env, home or Path.home()
    if system == 'windows':
        return Path(env.get('APPDATA', str(home / 'AppData' / 'Roaming'))) / '.minecraft'
    return home / ('Library/Application Support/minecraft' if system == 'osx' else '.minecraft')


def applies(rules, system=None, arch=None):
    """Mojang rules: a nonempty list starts denied, then the last matching rule wins."""
    allowed = not rules
    system, arch = system or system_name(), arch or platform.machine()
    for rule in rules:
        match = rule.get('os', {})
        if match.get('name', system) != system:
            continue
        if 'arch' in match and not re.fullmatch(match['arch'], arch):
            continue
        if 'version' in match and not re.search(match['version'], platform.version()):
            continue
        if any(value for value in rule.get('features', {}).values()):
            continue  # dev launches have no demo/quick-play/custom-resolution features enabled
        allowed = rule['action'] == 'allow'
    return allowed


def digest(path, algorithm='sha256'):
    h = hashlib.new(algorithm)
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def fetch(url, target, expected=None, algorithm='sha1', size=None, cache=None):
    """Verify cached and downloaded bytes; replace atomically only after validation."""
    def valid(path):
        return path.is_file() and (size is None or path.stat().st_size == size) and (
            digest(path, algorithm) == expected if expected else path.stat().st_size > 0)
    if valid(target):
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    if cache and expected and valid(cache):
        with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as stream:
            temporary = Path(stream.name)
            with cache.open('rb') as source:
                shutil.copyfileobj(source, stream)
        if not valid(temporary):
            temporary.unlink()
            raise RuntimeError(f'local cache changed while copying: {cache}')
        temporary.replace(target)
        return
    request = urllib.request.Request(url, headers={'User-Agent': 'Forbric-dev/1.0'})
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as stream:
            temporary = Path(stream.name)
            for attempt in range(3):
                try:
                    with urllib.request.urlopen(request, timeout=30, context=download_context()) as response:
                        stream.seek(0)
                        stream.truncate()
                        shutil.copyfileobj(response, stream)
                    break
                except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
                    if isinstance(error, urllib.error.HTTPError) and error.code not in (429, 500, 502, 503, 504):
                        raise
                    if attempt == 2:
                        raise
                    time.sleep(attempt + 1)
        if not valid(temporary):
            raise RuntimeError(f'download digest/size mismatch: {url}')
        temporary.replace(target)
    finally:
        if temporary and temporary.exists():
            temporary.unlink()


def confined(root, relative):
    path = (root / relative).resolve()
    if not path.is_relative_to(root.resolve()) or path == root.resolve():
        raise RuntimeError(f'unsafe artifact path: {relative}')
    return path


def libraries(metadata, system=None, arch=None):
    system, arch = system or system_name(), arch or platform.machine()
    arm = arch.lower() in ('arm64', 'aarch64')
    x86 = arch.lower() in ('x86', 'i386', 'i686')
    for lib in metadata.get('libraries', []):
        if not applies(lib.get('rules', []), system, arch):
            continue
        classifier_name = lib.get('name', '').split(':')[-1]
        if classifier_name.startswith('natives-'):
            if classifier_name.endswith('-arm64') != arm:
                continue
            if classifier_name.endswith('-x86') != x86:
                continue
        downloads = lib.get('downloads', {})
        if downloads.get('artifact'):
            yield downloads['artifact']
        classifier = lib.get('natives', {}).get(system)
        if classifier:
            classifier = classifier.replace('${arch}', '64' if sys.maxsize > 2**32 else '32')
            native = downloads.get('classifiers', {}).get(classifier)
            if native:
                yield native


NO_ASSETS = '.no-assets'


def assets_skipped(mc):
    """True when this Minecraft directory was prepared with --no-assets (enough for tests and dedicated servers)."""
    return (mc / NO_ASSETS).is_file()


def stage_minecraft(mc, native_dir, arch=None, assets=True):
    metadata = json.loads((mc / f'versions/{MC_VERSION}/{MC_VERSION}.json').read_text())
    native_dir.mkdir(parents=True, exist_ok=True)
    entries = list(libraries(metadata, arch=arch))
    print(f'[dev] Resolving {len(entries)} platform libraries' + (' and Minecraft assets' if assets else ''), flush=True)
    for entry in entries:
        jar = confined(mc / 'libraries', entry['path'])
        fetch(entry['url'], jar, entry['sha1'], size=entry.get('size'),
              cache=confined(default_minecraft_dir() / 'libraries', entry['path']))
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.endswith(('.dll', '.so', '.dylib', '.jnilib')):
                    confined(native_dir, name)  # validate the archive path before flattening native filenames
                    destination = native_dir / Path(name).name
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(archive.read(name))
    if assets:
        stage_assets(mc, metadata)
        (mc / NO_ASSETS).unlink(missing_ok=True)
    else:
        # Unit tests and dedicated-server gates never read assets; only the client does. Leave a marker so a later
        # client launch knows to fetch them instead of reporting a broken install.
        (mc / NO_ASSETS).write_text('prepared with --no-assets\n')
        print('[dev] Assets skipped (--no-assets): enough for tests and dedicated servers, not for the client', flush=True)
    # NeoForge's runtime omits JLine as a game-provided library, but vanilla metadata does not list it.
    for name, sha in CONSOLE_PINS:
        relative = f'org/jline/{name}/3.25.1/{name}-3.25.1.jar'
        fetch('https://repo.maven.apache.org/maven2/' + relative, mc / 'libraries' / relative,
              sha, 'sha256', cache=default_minecraft_dir() / 'libraries' / relative)


def stage_assets(mc, metadata):
    index = metadata['assetIndex']
    path = mc / 'assets/indexes' / (index['id'] + '.json')
    fetch(index['url'], path, index['sha1'], size=index.get('size'),
          cache=default_minecraft_dir() / 'assets/indexes' / (index['id'] + '.json'))
    objects = json.loads(path.read_text())['objects']
    def asset(entry):
        sha1 = entry['hash']
        if not re.fullmatch(r'[0-9a-f]{40}', sha1):
            raise RuntimeError('invalid asset hash')
        relative = sha1[:2] + '/' + sha1
        fetch('https://resources.download.minecraft.net/' + relative, mc / 'assets/objects' / relative,
              sha1, size=entry.get('size'), cache=default_minecraft_dir() / 'assets/objects' / relative)
    with ThreadPoolExecutor(max_workers=8) as pool:
        for count, _ in enumerate(pool.map(asset, objects.values()), 1):
            if count % 1000 == 0:
                print(f'[dev] Assets: {count}/{len(objects)}', flush=True)
    print(f'[dev] {len(objects)} assets ready', flush=True)


def java_bin(requested=None):
    requested = requested or os.environ.get('FORBRIC_JAVA')
    if requested:
        path = Path(requested).expanduser()
        if path.is_dir():
            path /= 'bin/java.exe' if os.name == 'nt' else 'bin/java'
        return str(path.resolve()) if path.exists() else (shutil.which(requested) or requested)
    if os.environ.get('JAVA_HOME'):
        return str(Path(os.environ['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
    return shutil.which('java') or 'java'


def java_environment(java, minimum=25):
    result = subprocess.run([java, '-XshowSettings:properties', '-version'], capture_output=True, text=True, check=True)
    values = dict(re.findall(r'^\s*(java\.specification\.version|java\.home|os\.arch)\s*=\s*(.*?)\s*$',
                             result.stderr + result.stdout, re.M))
    feature = int(values['java.specification.version'])
    if feature < minimum:
        raise RuntimeError(f'JDK {minimum}+ required for this command; selected Java {feature}')
    home = Path(values['java.home'])
    if not (home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')).is_file():
        raise RuntimeError(f'a full JDK is required, not a JRE: {home}')
    return dict(os.environ, JAVA_HOME=str(home), FORBRIC_DEV_ARCH=values['os.arch'])


def gradle(module, arguments, env, capture=False):
    wrapper = ROOT / module / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    command = [str(wrapper), '-p', str(wrapper.parent)] + list(arguments)
    # Explicit cmd invocation for .bat wrappers; subprocess still quotes paths with spaces.
    if os.name == 'nt':
        command = ['cmd', '/d', '/c'] + command
    result = subprocess.run(command, cwd=ROOT, env=env, check=True, stdout=subprocess.PIPE if capture else None, text=capture)
    return result.stdout if capture else None


def kernel_libraries(env, mc, stage):
    """sponge-mixin and ASM as the kernel resolves them, which a vanilla Minecraft tree does not have."""
    # The kernel's prepareDev task hands its classpath over rather than have this start a second build of itself.
    classpath = env.get('FORBRIC_KERNEL_CLASSPATH') or (gradle(
        'forbric-kernel', ['-q', 'printBootClasspath'] + build_properties(mc, stage), env, capture=True).strip().splitlines() or [''])[-1]
    jars = [Path(p) for p in classpath.split(os.pathsep) if p.endswith('.jar')]
    chosen = [p for p in jars if p.name.startswith(('sponge-mixin-', 'asm-'))]
    for prefix in ('sponge-mixin-', 'asm-tree-'):
        if not any(p.name.startswith(prefix) and p.is_file() for p in chosen):
            raise RuntimeError(f'the kernel classpath has no {prefix}*.jar for the canary mods')
    return chosen


def stage_canaries(stage, libraries, java_home, sources=CANARY_SOURCES):
    """Build CANARIES into the stage as build-testmods.sh does, replacing what an earlier prepare left."""
    suffix = '.exe' if os.name == 'nt' else ''
    javac, jar = (str(Path(java_home) / 'bin' / (tool + suffix)) for tool in ('javac', 'jar'))
    for source, output, release, game in CANARIES:
        tree = stage / source
        if tree.exists():
            shutil.rmtree(tree)
        shutil.copytree(sources / source, tree)
        target = stage / output
        target.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='forbric-canary-') as scratch:
            classes = Path(scratch) / 'classes'
            classpath = os.pathsep.join(str(p) for p in [stage / jar_path for jar_path in game] + list(libraries))
            subprocess.run([javac, '--release', release, '-proc:none', '-cp', classpath, '-d', str(classes)]
                           + sorted(str(p) for p in tree.rglob('*.java')), check=True)
