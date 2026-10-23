import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('patch_dynamic', Path(__file__).resolve().parent.parent / 'patch-dynamic-torches.py')
repair = importlib.util.module_from_spec(spec)
spec.loader.exec_module(repair)


class DynamicTorchesPatchTest(unittest.TestCase):
    def test_archive_preserves_all_other_resources_and_the_original(self):
        with tempfile.TemporaryDirectory() as tmp:
            source, destination = Path(tmp)/'original.jar', Path(tmp)/'fixed.jar'
            predicate = {'condition':'minecraft:entity_properties','entity':'this','predicate':{'type':'minecraft:item','slots':{'contents':{'items':'#dt:lightemitting'}}}}
            resources = {'fabric.mod.json':json.dumps({'id':'mr_dynamic_torches','version':'5.4'}).encode(), repair.RESOURCE:json.dumps(predicate).encode(), 'data/dt/function/tag.mcfunction':b'execute if predicate dt:luminousitems run say lit\r\n'}
            with zipfile.ZipFile(source,'w') as archive:
                for name,data in resources.items(): archive.writestr(name,data)
            original=source.read_bytes()
            repair.patch(source,destination)
            self.assertEqual(original,source.read_bytes())
            with zipfile.ZipFile(destination) as archive:
                changed=json.loads(archive.read(repair.RESOURCE))['predicate']
                self.assertEqual('minecraft:item',changed['entity_type'])
                self.assertNotIn('type',changed)
                self.assertEqual(predicate['predicate']['slots'],changed['slots'])
                for name,data in resources.items():
                    if name != repair.RESOURCE: self.assertEqual(data,archive.read(name))

    def test_already_updated_json_is_byte_preserved(self):
        raw=b'{"condition":"minecraft:entity_properties","entity":"this","predicate":{"entity_type":"minecraft:item"}}'
        self.assertEqual(raw,repair.patched_predicate(raw))

    def test_ambiguous_predicate_and_in_place_changes_are_refused(self):
        with self.assertRaises(ValueError): repair.patched_predicate(b'{"condition":"minecraft:entity_properties","entity":"this","predicate":{"type":"minecraft:creeper"}}')
        with self.assertRaises(ValueError): repair.patch('same.jar','same.jar')


if __name__ == '__main__': unittest.main()
