import io
import json
import unittest
import zipfile
from archive import jar_ids
from dependency_selection import select_provider


def jar(entries):
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w') as archive:
        for name, value in entries.items():
            archive.writestr(name, value)
    return data.getvalue()


class ArchiveDependenciesTest(unittest.TestCase):
    def test_existing_api_dependency_wins_over_an_unrelated_subject(self):
        rows = {'app.jar': dict(loader='fabric', slug='app', kind='random'),
                'api.jar': dict(loader='fabric', slug='fabric-api', kind='dep'),
                'peer.jar': dict(loader='fabric', slug='peer', kind='random')}
        self.assertEqual('api.jar', select_provider('fabric-lifecycle-events-v1', 'app.jar',
                         ['peer.jar', 'api.jar'], rows, {'api.jar'}))
        self.assertEqual('api.jar', select_provider('fabric-lifecycle-events-v1', 'app.jar',
                         ['peer.jar', 'api.jar'], rows, set()))

    def test_an_explicit_required_subject_is_not_silently_replaced(self):
        rows = {'app.jar': dict(loader='fabric', slug='app', kind='random'),
                'lib.jar': dict(loader='fabric', slug='lib', kind='dep'),
                'peer.jar': dict(loader='fabric', slug='peer', kind='popular')}
        self.assertEqual('peer.jar', select_provider('shared', 'app.jar',
                         ['lib.jar', 'peer.jar'], rows, {'peer.jar'}))
    def test_nested_requirements_are_not_lost(self):
        child = jar({'fabric.mod.json': json.dumps(dict(schemaVersion=1, id='child', version='1', depends={'fabric-api-base': '*'}))})
        host = jar({'fabric.mod.json': json.dumps(dict(schemaVersion=1, id='host', version='1', jars=[dict(file='META-INF/jars/child.jar')])), 'META-INF/jars/child.jar': child})
        provided, required = jar_ids(host)
        self.assertEqual({'host', 'child'}, provided)
        self.assertEqual({'fabric-api-base'}, required)

    def test_jarjar_only_bundle_and_arbitrary_declared_path(self):
        child = jar({'META-INF/neoforge.mods.toml': 'modLoader="javafml"\n[[mods]]\nmodId="child"\nversion="1"\n[[dependencies.child]]\nmodId="api"\ntype="required"\n'})
        host = jar({'META-INF/jarjar/metadata.json': json.dumps(dict(jars=[dict(path='private/payload.jar')])), 'private/payload.jar': child})
        self.assertEqual(({'child'}, {'api'}), jar_ids(host))

    def test_an_internal_provider_satisfies_a_nested_requirement(self):
        child = jar({'fabric.mod.json': json.dumps(dict(schemaVersion=1, id='child', version='1', depends={'host': '*'}))})
        host = jar({'fabric.mod.json': json.dumps(dict(schemaVersion=1, id='host', version='1', jars=[dict(file='child.jar')])), 'child.jar': child})
        self.assertEqual(({'host', 'child'}, set()), jar_ids(host))

    def test_a_raw_control_character_in_a_string_is_read_like_fabric_loader_reads_it(self):
        host = jar({'fabric.mod.json': '{"schemaVersion": 1, "id": "host", "version": "1",\n "description": "two\tcolumns\nand lines",\n "depends": {"lib": "*"}}'})
        self.assertEqual(({'host'}, {'lib'}), jar_ids(host))

    def test_missing_declared_payload_is_a_setup_failure(self):
        host = jar({'META-INF/jarjar/metadata.json': json.dumps(dict(jars=[dict(path='missing.jar')]))})
        with self.assertRaisesRegex(ValueError, 'declared nested jar missing'):
            jar_ids(host)


if __name__ == '__main__':
    unittest.main()
