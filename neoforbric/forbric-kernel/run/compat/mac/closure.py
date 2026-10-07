#!/usr/bin/env python3
"""For every jar in manifest.json, the set of OTHER jars it needs (transitively): Modrinth's required dependencies
of its version, the mod ids its own metadata requires, and the missing-dep rows recorded against it (needed_by).
Writes closure.json {filename: [dependency filenames]}. Unresolvable requirements are listed in closure-missing.json."""
import io, json, sys, tomllib, urllib.parse, zipfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
import api as p
from archive import jar_ids
from dependency_selection import select_provider

import os
HERE = Path(os.environ.get('PERMOD_DATA', str(p.HERE)))
manifest = json.loads((HERE / 'manifest.json').read_text())
by_file = {r['filename']: r for r in manifest}
IGNORE = {'minecraft', 'java', 'fabricloader', 'fabric-loader', 'neoforge', 'forge', 'quilt_loader', 'fml', 'javafml',
          'lowcodefml'}




provides, requires = {}, {}
for f in by_file:
    provides[f], requires[f] = jar_ids((HERE / 'mods' / f).read_bytes())
# id -> jars providing it
index = {}
for f, ids in provides.items():
    for i in ids:
        index.setdefault(i, []).append(f)
ALIAS = {'fabric': 'fabric-api', 'cloth_config': 'cloth-config', 'cloth-config2': 'cloth-config'}

direct, missing = {}, {}
for f, r in by_file.items():
    need = set()
    # Modrinth required deps
    v = p.get('/version/' + urllib.parse.quote(r['version_id'], safe=''))
    for d in v.get('dependencies', []):
        if d.get('dependency_type') != 'required':
            continue
        pid = d.get('project_id')
        if d.get('version_id') and not pid:
            pid = p.get('/version/' + d['version_id'])['project_id']
        cands = [g for g, s in by_file.items() if s['project_id'] == pid and g != f]
        same = [g for g in cands if by_file[g]['loader'] == r['loader']]
        if same or cands:
            need.add((same or cands)[0])
        else:
            missing.setdefault(f, []).append('modrinth:' + str(pid))
    # A module already supplied by an explicit dependency does not justify loading a second subject.
    explicit = set(need)
    # jar-declared required ids
    for mid in sorted(requires[f]):
        if mid in IGNORE or mid in provides[f]:
            continue
        mid2 = ALIAS.get(mid, mid)
        cands = [g for g in index.get(mid, []) + index.get(mid2, []) if g != f]
        selected = select_provider(mid, f, cands, by_file, explicit | need)
        if selected:
            need.add(selected)
        else:
            missing.setdefault(f, []).append('id:' + mid)
    # recorded missing-dep rows (found by testing)
    for g, s in by_file.items():
        if s['kind'] == 'missing-dep' and s['needed_by'] == r['slug'] and g != f:
            need.add(g)
    direct[f] = need

closure = {}
for f in by_file:
    seen, stack = set(), list(direct[f])
    while stack:
        g = stack.pop()
        if g in seen or g == f:
            continue
        seen.add(g); stack.extend(direct[g])
    closure[f] = sorted(seen)
(HERE / 'closure.json').write_text(json.dumps(closure, indent=1))
(HERE / 'closure-missing.json').write_text(json.dumps(missing, indent=1))
for f in sorted(closure):
    print(f'{len(closure[f]):2} {f}  missing={missing.get(f, [])}')
