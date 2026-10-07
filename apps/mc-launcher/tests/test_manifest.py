"""Regression tests for native extraction and library path handling.

Run from anywhere:

    python mc-launcher/tests/test_manifest.py

manifest.py imports PyQt6 (for the manifest fetcher signal) and requests,
neither of which is installed in a headless checkout. Inert stand-ins are
registered first when they are missing; the real packages are used when
present.
"""

import importlib.util
import shutil
import sys
import tempfile
import types
import unittest
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
if str(REPO) not in sys.path:
    sys.path.insert(0, str(REPO))


def _stub(name, **attrs):
    module = types.ModuleType(name)
    for key, value in attrs.items():
        setattr(module, key, value)
    sys.modules[name] = module
    return module


if importlib.util.find_spec("requests") is None:
    _stub("requests",
          Session=type("Session", (), {"__init__":
                                       lambda self: setattr(self, "headers", {})}),
          HTTPError=Exception)

if importlib.util.find_spec("PyQt6") is None:
    pyqt6 = _stub("PyQt6")
    _stub("PyQt6.QtCore",
          QObject=type("QObject", (), {"__init__": lambda self, *a, **k: None}),
          pyqtSignal=lambda *a: None)
    pyqt6.QtCore = sys.modules["PyQt6.QtCore"]

from mc_launcher.manifest import Downloader  # noqa: E402

PAYLOAD = b"native"


def native_jar(path, members):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as z:
        for member in members:
            z.writestr(member, PAYLOAD)
    return path


class ExtractNativesTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.mc_dir = self.tmp / ".minecraft"
        self.version_dir = self.mc_dir / "versions" / "1.20.1"
        self.version_dir.mkdir(parents=True)
        self.natives_dir = self.version_dir / "1.20.1-natives"
        self.messages = []
        self.dl = Downloader(log_callback=self.messages.append)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _version_json(self, jar_relpath):
        return {"libraries": [{
            "name": "org.lwjgl:lwjgl:3.3.1",
            "natives": {"windows": "natives-windows", "linux": "natives-linux",
                        "osx": "natives-osx"},
            "downloads": {"classifiers": {
                "natives-windows": {"path": jar_relpath},
            }},
        }]}

    def _extract_twice(self, members):
        """Return True when the second call extracted the jar again."""
        jar = native_jar(self.mc_dir / "libraries" / "natives" / "lwjgl.jar",
                         members)
        vj = self._version_json("natives/lwjgl.jar")
        self.dl._extract_natives(vj, self.mc_dir, self.version_dir)
        first = sorted(p.name for p in self.natives_dir.glob("*"))
        if not first:
            return None
        # If the guard short-circuits, the sentinel survives.
        for p in self.natives_dir.glob("*"):
            p.write_bytes(b"SENTINEL")
        self.dl._extract_natives(vj, self.mc_dir, self.version_dir)
        return any(p.read_bytes() == PAYLOAD for p in self.natives_dir.glob("*"))

    def test_windows_dll_natives_are_cached(self):
        self.assertFalse(self._extract_twice(["lwjgl.dll", "glfw.dll"]))

    def test_linux_so_natives_are_cached(self):
        # The guard used to look for *.dll only, so .so natives were
        # extracted again on every install.
        self.assertFalse(self._extract_twice(["liblwjgl.so", "libglfw.so"]))

    def test_macos_dylib_natives_are_cached(self):
        self.assertFalse(self._extract_twice(["liblwjgl.dylib", "libglfw.dylib"]))

    def test_meta_inf_is_skipped(self):
        self.dl._extract_natives(
            self._version_json("natives/lwjgl.jar"), self.mc_dir, self.version_dir)
        native_jar(self.mc_dir / "libraries" / "natives" / "lwjgl.jar",
                   ["META-INF/MANIFEST.MF", "lwjgl.dll"])
        self.dl._extract_natives(
            self._version_json("natives/lwjgl.jar"), self.mc_dir, self.version_dir)
        self.assertFalse((self.natives_dir / "META-INF").exists())

    def test_traversal_in_the_jar_path_is_refused(self):
        native_jar(self.tmp / "outside.jar", ["evil.dll"])
        vj = self._version_json("../../outside.jar")
        self.dl._extract_natives(vj, self.mc_dir, self.version_dir)
        self.assertEqual(list(self.natives_dir.glob("*")), [])
        self.assertTrue(any("Refusing" in m for m in self.messages))


if __name__ == "__main__":
    unittest.main(verbosity=2)
