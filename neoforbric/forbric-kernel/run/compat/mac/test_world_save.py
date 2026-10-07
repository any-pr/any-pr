import os
from pathlib import Path
import tempfile
import unittest
from world_save import saved_since


class WorldSaveTest(unittest.TestCase):
    def write(self, root, name, modified):
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b'fixture')
        os.utime(path, (modified, modified))

    def test_current_and_legacy_layouts_with_unchanged_regions(self):
        for region, player in [('region/r.0.0.mca', 'playerdata/player.dat'),
                               ('dimensions/minecraft/overworld/region/r.0.0.mca', 'players/data/player.dat')]:
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.write(root, 'level.dat', 200)
                self.write(root, region, 50)
                self.write(root, player, 200)
                self.assertTrue(saved_since(root, 100))
                self.assertFalse(saved_since(root, 300))

    def test_copied_world_and_incomplete_save_do_not_count(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, 'level.dat', 50)
            self.write(root, 'region/r.0.0.mca', 50)
            self.write(root, 'playerdata/player.dat', 50)
            self.assertFalse(saved_since(root, 100))
            self.write(root, 'level.dat', 200)
            self.assertFalse(saved_since(root, 100))
            self.write(root, 'playerdata/player.dat', 200)
            (root / 'region/r.0.0.mca').unlink()
            self.assertFalse(saved_since(root, 100))


if __name__ == '__main__':
    unittest.main()
