"""Regression tests for the mc-launcher path and rule helpers.

Run from anywhere:

    python mc-launcher/tests/test_util.py

Standard library only. ``mc_launcher.util`` imports nothing third-party, so
these tests need neither PyQt6 nor requests -- unlike the rest of the
package, which cannot be imported headless without them.
"""

import platform
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from mc_launcher.util import (  # noqa: E402
    maven_to_path,
    rules_allow,
    safe_under,
)


def allow(rules, system="Windows", machine="AMD64"):
    """Evaluate rules_allow against a fixed platform."""
    with mock.patch.object(platform, "system", return_value=system), \
         mock.patch.object(platform, "machine", return_value=machine):
        return rules_allow(rules)


class RulesAllowTests(unittest.TestCase):
    def test_no_rules_means_allowed(self):
        self.assertTrue(allow([]))
        self.assertTrue(allow(None))

    def test_matching_architecture_is_allowed(self):
        self.assertTrue(allow([{"action": "allow", "os": {"arch": "x86_64"}}]))
        self.assertTrue(allow([{"action": "allow", "os": {"arch": "x86"}}],
                              machine="x86"))

    def test_foreign_architecture_rule_does_not_apply(self):
        # A rule gated to arm64 must not be honoured on an x86_64 host. It
        # used to be: only "x86" and "x86_64" were compared, so any other
        # value fell through and the rule applied everywhere.
        self.assertFalse(allow([{"action": "allow", "os": {"arch": "arm64"}}]))
        self.assertFalse(allow([{"action": "allow", "os": {"arch": "aarch64"}}],
                               system="Linux", machine="x86_64"))

    def test_foreign_architecture_disallow_does_not_apply(self):
        # Nothing matches, and a rule list with no match denies. The old
        # code reached the same verdict by the wrong route -- applying the
        # disallow -- so this pins the behaviour, not the path taken.
        self.assertFalse(allow([{"action": "disallow", "os": {"arch": "arm64"}}]))

    def test_unmatched_rules_deny(self):
        self.assertFalse(allow([{"action": "allow", "os": {"name": "osx"}}]))

    def test_operating_system_match(self):
        self.assertTrue(allow([{"action": "allow", "os": {"name": "windows"}}]))
        self.assertFalse(allow([{"action": "allow", "os": {"name": "linux"}}]))


class MavenToPathTests(unittest.TestCase):
    def test_canonical_coordinates(self):
        rel, rel_dir = maven_to_path("net.fabricmc:fabric-loader:0.15.11")
        self.assertEqual(rel, "net/fabricmc/fabric-loader/0.15.11/fabric-loader-0.15.11.jar")
        self.assertEqual(rel_dir, "net/fabricmc/fabric-loader/0.15.11")

    def test_classifier(self):
        rel, _ = maven_to_path("org.lwjgl:lwjgl:3.3.1:natives-windows")
        self.assertEqual(rel, "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-windows.jar")

    def test_rejects_malformed_coordinates(self):
        for coords in ["", "artifact", "group:artifact", ":artifact:1.0",
                       "group:artifact:", "group::1.0"]:
            self.assertEqual(maven_to_path(coords), (None, None), coords)

    def test_rejects_segments_that_traverse(self):
        for coords in ["..:artifact:1.0", "group:..:1.0", "group:artifact:..",
                       "a/../b:artifact:1.0",
                       "group:artifact:1.0:../escape"]:
            self.assertEqual(maven_to_path(coords), (None, None), coords)

    def test_paths_stay_under_the_libraries_directory(self):
        root = Path("D:/mc") / "libraries"
        coords = ["net.fabricmc:fabric-loader:0.15.11",
                  "org.lwjgl:lwjgl:3.3.1:natives-windows",
                  ":artifact:1.0",
                  "..:artifact:1.0",
                  "group:..:1.0"]
        for c in coords:
            rel, _ = maven_to_path(c)
            if rel is None:
                continue
            joined = (root / rel).resolve()
            self.assertEqual(
                joined, (root / rel).resolve(),
                "{} escaped: {}".format(c, joined))

    def test_no_relative_path_is_rooted(self):
        # A leading separator made Path(libraries) / rel resolve to the
        # drive root, so no returned path may start with one.
        for c in ["net.fabricmc:fabric-loader:0.15.11", ":artifact:1.0",
                  "..:artifact:1.0"]:
            rel, rel_dir = maven_to_path(c)
            for value in (rel, rel_dir):
                if value is None:
                    continue
                self.assertFalse(value.startswith("/") or value.startswith("\\"),
                                 "{} produced rooted path {}".format(c, value))


class SafeUnderTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.base = self.tmp / "mods"
        self.base.mkdir()

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_plain_file_name(self):
        self.assertEqual(safe_under(self.base, "fabric-api.jar"),
                         (self.base / "fabric-api.jar").resolve())

    def test_subdirectory_is_allowed(self):
        target = safe_under(self.base, "sub/fabric-api.jar")
        self.assertIsNotNone(target)
        self.assertEqual(target.parent, (self.base / "sub").resolve())

    def test_parent_traversal_is_refused(self):
        self.assertIsNone(safe_under(self.base, "../evil.jar"))
        self.assertIsNone(safe_under(self.base, "../../evil.jar"))
        self.assertIsNone(safe_under(self.base, "sub/../../evil.jar"))

    def test_rooted_path_is_refused(self):
        self.assertIsNone(safe_under(self.base, "/abs/evil.jar"))

    def test_traversal_settling_back_inside_is_allowed(self):
        # "sub/../api.jar" resolves back into base, so it is not an escape.
        self.assertEqual(safe_under(self.base, "sub/../api.jar"),
                         (self.base / "api.jar").resolve())


if __name__ == "__main__":
    unittest.main(verbosity=2)
