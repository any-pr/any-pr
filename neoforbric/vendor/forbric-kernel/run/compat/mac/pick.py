#!/usr/bin/env python3
"""Select N unseen projects: up to 38 in 100 popular from the top 200, then random projects; dependencies are extra.
Usage: [PICK_LOADER=fabric|neoforge|forge] [PICK_COUNT=100] [PICK_SIDE=server] pick.py <data-dir> <seed> <exclude-manifest> ...

Without PICK_LOADER each subject's loader is a seeded choice among its 26.2 builds. With it every subject is that
loader's build and a project without one is skipped, so a pack of one ecosystem is drawn from the same pools.
PICK_SIDE=server keeps only projects a dedicated server runs: Modrinth must list the server side as required or
optional, and a Fabric subject's own fabric.mod.json must not declare environment "client".
"""
import io, json, os, random, sys, tomllib, urllib.error, urllib.parse, zipfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
import api as p
from archive import environment, jar_ids

IGNORE = {'minecraft', 'java', 'fabricloader', 'fabric-loader', 'neoforge', 'forge', 'quilt_loader', 'fml', 'javafml',
          'lowcodefml'}
SIDES = ('server',)


def choose_loader(builds, rng, forced=None):
    """The loader whose build a project contributes, or None to skip it.

    Forced, a project without that loader's build is skipped rather than substituted: a NeoForge build is not a
    Fabric mod. Otherwise the choice is seeded over the sorted loaders, so it does not depend on dict order.
    """
    if forced:
        return forced if forced in builds else None
    return rng.choice(sorted(builds)) if builds else None


def popular_share(count):
    """How many of `count` subjects come from the popular pool: 38 of 100, as every earlier sweep drew them."""
    return round(count * 38 / 100)


def settings(environ):
    forced = environ.get('PICK_LOADER') or None
    if forced and forced not in p.LOADERS:
        raise SystemExit(f'PICK_LOADER must be one of {", ".join(p.LOADERS)}, not {forced!r}')
    count = int(environ.get('PICK_COUNT', '100'))
    if count < 1:
        raise SystemExit('PICK_COUNT must be positive')
    side = environ.get('PICK_SIDE') or None
    if side and side not in SIDES:
        raise SystemExit(f'PICK_SIDE must be one of {", ".join(SIDES)}, not {side!r}')
    return forced, count, side


def facets(forced, side):
    rows = [[f'versions:{p.MC}'], ['project_type:mod'], [f'categories:{l}' for l in ((forced,) if forced else p.LOADERS)]]
    if side == 'server':
        rows.append(['server_side:required', 'server_side:optional'])
    return json.dumps(rows)


def main(argv=None, environ=None):
    argv = sys.argv[1:] if argv is None else argv
    if len(argv) < 2:
        raise SystemExit(__doc__)
    DATA, SEED, EXCLUDE = Path(argv[0]).resolve(), int(argv[1]), [Path(x) for x in argv[2:]]
    forced, count, side = settings(os.environ if environ is None else environ)
    rng = random.Random(SEED)
    FACETS = facets(forced, side)
    DATA.mkdir(parents=True, exist_ok=True)
    excluded_pids = set()
    for m in EXCLUDE:
        for r in json.loads(m.read_text()):
            if r.get('project_id'):
                excluded_pids.add(r['project_id'])
    selected, picked = {}, set()
    incompatible = set()

    def add(slug, pid, loader, version, kind, needed_by=''):
        key = (pid, loader)
        if key in selected:
            return False
        a = p.primary(version)
        fname = p.safe_filename(a['filename'])
        if fname.casefold() in {r['filename'].casefold() for r in selected.values()}:
            fname = f'{loader}-{fname}'
        selected[key] = dict(kind=kind, slug=slug, project_id=pid, loader=loader, version=version['version_number'],
                             version_id=version['id'], version_type=version.get('version_type'), loaders=version['loaders'],
                             filename=fname, source_filename=a['filename'], url=a['url'],
                             size=a['size'], sha1=a['hashes']['sha1'], needed_by=needed_by)
        for d in version.get('dependencies', []):
            if d.get('dependency_type') == 'incompatible' and d.get('project_id'):
                incompatible.add(d['project_id'])
        print(f'  + {kind:11} {slug} [{loader}] {version["version_number"]}' + (f'  (for {needed_by})' if needed_by else ''), flush=True)
        return True

    def side_ok(hit, version, loader):
        if hit.get('server_side') not in ('required', 'optional'):
            return False
        if loader != 'fabric':
            return True
        # The jar is the authority Fabric Loader itself reads; Modrinth's side flags are the author's form entry.
        a = p.primary(version)
        cached = DATA / 'probe' / (a['hashes']['sha1'] + '.jar')
        if not (cached.is_file() and p.sha1(cached) == a['hashes']['sha1']):
            cached.parent.mkdir(parents=True, exist_ok=True)
            cached.write_bytes(p.fetch(a['url']))
            if p.sha1(cached) != a['hashes']['sha1']:
                raise SystemExit('hash mismatch ' + a['filename'])
        return environment(cached.read_bytes()) in ('*', 'server')

    def download(row):
        target = DATA / 'mods' / row['filename']
        if not (target.is_file() and target.stat().st_size == row['size'] and p.sha1(target) == row['sha1']):
            probed = DATA / 'probe' / (row['sha1'] + '.jar')
            target.write_bytes(probed.read_bytes() if probed.is_file() else p.fetch(row['url']))
            if target.stat().st_size != row['size'] or p.sha1(target) != row['sha1']:
                raise SystemExit('hash/size mismatch ' + row['filename'])
        row['sha1_ok'] = True

    def pick(hits, want, kind):
        got = 0
        for h in hits:
            if got == want:
                break
            pid, slug = h['project_id'], h['slug']
            if pid in picked or pid in excluded_pids:
                continue
            builds = p.loader_builds(pid)
            loader = choose_loader(builds, rng, forced)
            if loader is None:
                continue
            if side and not side_ok(h, builds[loader], loader):
                continue
            mine = {d.get('project_id') for d in builds[loader].get('dependencies', []) if d.get('dependency_type') == 'incompatible'}
            picked.add(pid)
            got += add(slug, pid, loader, builds[loader], kind)
        return got

    top = []
    for off in (0, 100):
        top += p.get('/search', facets=FACETS, index='downloads', limit=100, offset=off)['hits']
    rng.shuffle(top)
    (DATA / 'pool.json').write_text(json.dumps(dict(seed=SEED, loader=forced, count=count, side=side, facets=FACETS,
                                                    popular=top), indent=1) + '\n')
    print('popular (top 200 by downloads):')
    n_pop = pick(top, min(popular_share(count), sum(h['project_id'] not in excluded_pids for h in top)), 'popular')
    wanted_random = count - n_pop
    total = p.get('/search', facets=FACETS, limit=1)['total_hits']
    offsets = list(range(0, min(total, 10000), 100))
    rng.shuffle(offsets)
    pool = []
    for off in offsets[:15]:
        pool += p.get('/search', facets=FACETS, index='newest', limit=100, offset=off)['hits']
    rng.shuffle(pool)
    saved_pool = json.loads((DATA / 'pool.json').read_text())
    saved_pool.update(random=pool, total=total)
    (DATA / 'pool.json').write_text(json.dumps(saved_pool, indent=1) + '\n')
    print(f'random (pool {len(pool)} of {total}):')
    n_rand = pick(pool, wanted_random, 'random')

    (DATA / 'mods').mkdir(parents=True, exist_ok=True)
    queue, done = list(selected.values()), set()
    unresolved = {}
    while queue:
        row = queue.pop(0)
        if row['filename'] in done:
            continue
        done.add(row['filename'])
        download(row)
        version = next(v for v in p.versions(row['project_id']) if v['id'] == row['version_id'])
        # Modrinth's required dependencies, for the dependent's loader
        for d in version.get('dependencies', []):
            if d.get('dependency_type') != 'required':
                continue
            pid, vid, dep = d.get('project_id'), d.get('version_id'), None
            if vid:
                dep = p.get('/version/' + urllib.parse.quote(vid, safe=''))
                pid = dep['project_id']
                if p.MC not in dep.get('game_versions', []) or not p.primary(dep):
                    dep = None
            if not pid or (pid, row['loader']) in selected:
                continue
            dep = dep or p.loader_builds(pid).get(row['loader'])
            if dep is None:
                unresolved.setdefault(row['slug'], []).append('modrinth:' + pid)
                continue
            if add(p.get('/project/' + pid)['slug'], pid, row['loader'], dep, 'dep', row['slug']):
                queue.append(selected[(pid, row['loader'])])
        # required mod ids from the jar itself that nothing selected provides
        provided = set()
        for other in selected.values():
            f = DATA / 'mods' / other['filename']
            if f.exists():
                provided |= jar_ids(f.read_bytes())[0]
        _, req = jar_ids((DATA / 'mods' / row['filename']).read_bytes())
        for mid in sorted(req - IGNORE - provided):
            if mid.startswith('fabric-') and any(s['slug'] == 'fabric-api' for s in selected.values()):
                continue
            found = None
            aliases = {'fabric': 'fabric-api', 'cloth_config': 'cloth-config', 'cloth_config2': 'cloth-config', 'kotlinforforge': 'kotlin-for-forge'}
            candidates = ['fabric-api'] if mid.startswith('fabric-') else [aliases.get(mid, mid), mid, mid.replace('_', '-')]
            for cand in dict.fromkeys(candidates):
                try:
                    proj = p.get('/project/' + urllib.parse.quote(cand, safe=''))
                except urllib.error.HTTPError:
                    continue
                b = p.loader_builds(proj['id']).get(row['loader'])
                if b:
                    found = (proj, b)
                    break
            if not found:
                unresolved.setdefault(row['slug'], []).append('id:' + mid)
                continue
            proj, b = found
            if add(proj['slug'], proj['id'], row['loader'], b, 'missing-dep', row['slug']):
                queue.append(selected[(proj['id'], row['loader'])])
    manifest = list(selected.values())
    names = {}
    for r in manifest:
        if r['filename'].casefold() in names:
            raise SystemExit('filename collision ' + r['filename'])
        names[r['filename'].casefold()] = r['slug']
    (DATA / 'manifest.json').write_text(json.dumps(manifest, indent=1) + '\n')
    (DATA / 'unresolved.json').write_text(json.dumps(unresolved, indent=1) + '\n')
    from collections import Counter
    print('total jars', len(manifest), Counter(r['kind'] for r in manifest), Counter(r['loader'] for r in manifest))
    print('unresolved', unresolved)
    return 0 if n_pop + n_rand == count else 2


if __name__ == '__main__':
    sys.exit(main())
