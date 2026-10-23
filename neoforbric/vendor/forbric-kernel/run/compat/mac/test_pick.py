"""pick.py: loader choice, the PICK_* settings, and a whole selection against a fake Modrinth."""
import hashlib
import io
import json
import random
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

import api
import pick
from archive import environment


def jar(mod_id, env=None):
    metadata = dict(schemaVersion=1, id=mod_id, version='1')
    if env:
        metadata['environment'] = env
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps(metadata))
    return data.getvalue()


def version(pid, loader, data):
    return dict(id=f'{pid}-{loader}', project_id=pid, version_number='1.0', loaders=[loader], game_versions=[api.MC],
                dependencies=[], files=[dict(filename=f'{pid}-{loader}.jar', primary=True, url=f'https://cdn/{pid}-{loader}.jar',
                                             size=len(data), hashes=dict(sha1=hashlib.sha1(data).hexdigest()))])


class ChooseLoader(unittest.TestCase):
    def test_a_forced_loader_skips_a_project_without_that_build(self):
        self.assertIsNone(pick.choose_loader({'neoforge': {}}, random.Random(1), 'fabric'))

    def test_a_forced_loader_is_taken_without_drawing_from_the_rng(self):
        rng, untouched = random.Random(7), random.Random(7)
        self.assertEqual('fabric', pick.choose_loader({'neoforge': {}, 'fabric': {}}, rng, 'fabric'))
        self.assertEqual(untouched.random(), rng.random())

    def test_the_seeded_choice_repeats_and_ignores_dict_order(self):
        first, second = random.Random(20260926), random.Random(20260926)
        one = [pick.choose_loader({'neoforge': 1, 'fabric': 1, 'forge': 1}, first, None) for _ in range(20)]
        two = [pick.choose_loader({'forge': 1, 'fabric': 1, 'neoforge': 1}, second, None) for _ in range(20)]
        self.assertEqual(one, two)
        self.assertEqual({'fabric', 'forge', 'neoforge'}, set(one))

    def test_no_build_is_a_skip_either_way(self):
        self.assertIsNone(pick.choose_loader({}, random.Random(1), None))


class Settings(unittest.TestCase):
    def test_defaults_are_the_earlier_sweeps(self):
        self.assertEqual((None, 100, None), pick.settings({}))
        self.assertEqual(38, pick.popular_share(100))
        # Unforced facets are byte-identical to api.FACETS, so an earlier seed still draws the same pools.
        self.assertEqual(api.FACETS, pick.facets(None, None))

    def test_a_forced_loader_searches_only_its_category(self):
        self.assertEqual(('fabric', 130, 'server'), pick.settings(dict(PICK_LOADER='fabric', PICK_COUNT='130', PICK_SIDE='server')))
        rows = json.loads(pick.facets('fabric', 'server'))
        self.assertIn(['categories:fabric'], rows)
        self.assertIn(['server_side:required', 'server_side:optional'], rows)
        self.assertEqual(49, pick.popular_share(130))

    def test_unknown_values_are_refused(self):
        for bad in (dict(PICK_LOADER='quilt'), dict(PICK_COUNT='0'), dict(PICK_SIDE='client')):
            with self.assertRaises(SystemExit):
                pick.settings(bad)

    def test_importing_reads_no_command_line(self):
        # pick.py used to read sys.argv at import, so importing it under another program's argv raised.
        with mock.patch.object(sys, 'argv', ['unittest']):
            import importlib
            importlib.reload(pick)


class Environment(unittest.TestCase):
    def test_declared_and_default_sides(self):
        self.assertEqual('*', environment(jar('a')))
        self.assertEqual('client', environment(jar('a', 'client')))
        self.assertEqual('server', environment(jar('a', 'server')))


class WholeSelection(unittest.TestCase):
    """main() against a fake registry: forced Fabric, server side, two subjects."""

    def test_forced_fabric_server_pack(self):
        jars = {'srv': jar('srv'), 'both': jar('both', '*'), 'cli': jar('cli', 'client'), 'neo': jar('neo'), 'rnd': jar('rnd', 'server')}
        builds = {'srv': ['fabric'], 'both': ['fabric', 'neoforge'], 'cli': ['fabric'], 'neo': ['neoforge'], 'rnd': ['fabric']}
        versions = {pid: [version(pid, loader, jars[pid]) for loader in loaders] for pid, loaders in builds.items()}
        hit = lambda pid, side='optional': dict(project_id=pid, slug=pid, server_side=side)
        popular = [hit('neo'), hit('cli'), hit('srv', 'unsupported'), hit('both')]
        newest = [hit('rnd'), hit('srv', 'unsupported')]
        searches = []

        def get(route, **q):
            self.assertEqual('/search', route)
            searches.append(q['facets'])
            if q.get('limit') == 1:
                return dict(total_hits=len(newest))
            if q.get('index') == 'downloads':
                return dict(hits=popular if q['offset'] == 0 else [])
            return dict(hits=newest if q['offset'] == 0 else [])
        by_url = {version(pid, l, jars[pid])['files'][0]['url']: jars[pid] for pid, ls in builds.items() for l in ls}
        with tempfile.TemporaryDirectory() as tmp, \
                mock.patch.object(api, 'get', get), mock.patch.object(api, 'versions', lambda pid: versions[pid]), \
                mock.patch.object(api, 'fetch', lambda url: by_url[url]):
            code = pick.main([tmp, '5'], dict(PICK_LOADER='fabric', PICK_COUNT='2', PICK_SIDE='server'))
            manifest = json.loads((Path(tmp) / 'manifest.json').read_text())
        self.assertEqual(0, code)
        # neo has no Fabric build, cli's jar is client-only, srv's Modrinth server side is unsupported.
        self.assertEqual({('both', 'popular'), ('rnd', 'random')}, {(row['slug'], row['kind']) for row in manifest})
        self.assertEqual({'fabric'}, {row['loader'] for row in manifest})
        self.assertTrue(all(row['sha1_ok'] for row in manifest))
        self.assertTrue(all('categories:fabric' in facets and 'categories:neoforge' not in facets for facets in searches))


if __name__ == '__main__':
    unittest.main()
