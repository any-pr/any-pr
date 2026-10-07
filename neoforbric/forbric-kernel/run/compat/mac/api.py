#!/usr/bin/env python3
"""Modrinth requests and version selection for compatibility sweeps."""
import http.client, hashlib, json, os, random, re, sys, time, urllib.error, urllib.parse, urllib.request
from pathlib import Path

API = 'https://api.modrinth.com/v2'
MC = '26.2'
LOADERS = ('fabric', 'neoforge', 'forge')
HERE = Path(os.environ.get("PERMOD_DATA", str(Path(__file__).resolve().parents[3] / "build" / "sweep100-mac")))
OUT = HERE / 'mods'
SEED = int(os.environ.get('SEED', '20260926'))
rng = random.Random(SEED)


def fetch(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'Forbric-compat/1.0 (rt.ge.jerry@gmail.com)'})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=90) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code not in (429, 500, 502, 503, 504) or attempt == 4:
                raise
        except (urllib.error.URLError, TimeoutError, ConnectionError, OSError, http.client.HTTPException):
            if attempt == 4:
                raise
        time.sleep(2 * (attempt + 1))


def get(route, **q):
    return json.loads(fetch(API + route + ('?' + urllib.parse.urlencode(q) if q else '')))


FACETS = json.dumps([[f'versions:{MC}'], ['project_type:mod'], [f'categories:{l}' for l in LOADERS]])
_vcache = {}


def versions(pid):
    """All versions of a project that list MC 26.2, newest first, with a primary jar."""
    if pid not in _vcache:
        vs = get('/project/' + urllib.parse.quote(pid, safe='') + '/version', game_versions=json.dumps([MC]))
        _vcache[pid] = [v for v in vs if MC in v.get('game_versions', []) and primary(v)]
    return _vcache[pid]


def primary(v):
    files = [f for f in v.get('files', []) if f['filename'].lower().endswith('.jar')]
    return next((f for f in files if f.get('primary')), files[0] if files else None)


def loader_builds(pid):
    out = {}
    for v in versions(pid):
        for l in v.get('loaders', []):
            if l in LOADERS and l not in out:
                out[l] = v
    return out


def safe_filename(name):
    name = re.sub(r'[<>:"/\\|?*\x00-\x1f]', '_', name).rstrip(' .')
    return name


def sha1(path):
    h = hashlib.sha1()
    with open(path, 'rb') as f:
        for b in iter(lambda: f.read(1 << 20), b''):
            h.update(b)
    return h.hexdigest()

