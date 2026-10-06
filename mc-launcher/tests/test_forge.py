"""Regression tests for the Forge installer download.

Run from anywhere:

    python mc-launcher/tests/test_forge.py

forge.py imports requests but not PyQt6, so only requests needs a stand-in
when it is missing.
"""

import importlib.util
import os
import sys
import tempfile
import types
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
if str(REPO) not in sys.path:
    sys.path.insert(0, str(REPO))

if importlib.util.find_spec("requests") is None:
    _rq = types.ModuleType("requests")
    _rq.HTTPError = Exception

    class _Session:
        def __init__(self):
            self.headers = {}

    _rq.Session = _Session
    sys.modules["requests"] = _rq

from mc_launcher.forge import ForgeDownloader  # noqa: E402


class FakeResponse:
    """A response that delivers `deliver` of the `advertise` bytes it claims."""

    def __init__(self, advertise, deliver):
        self.headers = {"content-length": str(advertise)}
        self.status_code = 200
        self._deliver = deliver

    def raise_for_status(self):
        return None

    def iter_content(self, chunk_size):
        remaining = self._deliver
        while remaining > 0:
            n = min(chunk_size, remaining)
            yield b"\x50\x4b\x03\x04" * (n // 4) + b"\x00" * (n % 4)
            remaining -= n


class FakeSession:
    def __init__(self, advertise, deliver):
        self.headers = {}
        self.advertise = advertise
        self.deliver = deliver

    def get(self, *a, **k):
        return FakeResponse(self.advertise, self.deliver)


class DownloadInstallerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.target = (self.tmp / "forge-1.20.1-47.1.0-installer.jar")

    def _downloader(self, advertise, deliver):
        dl = ForgeDownloader(log_callback=lambda m: None)
        dl.session = FakeSession(advertise, deliver)
        return dl

    def test_complete_download_is_written(self):
        dl = self._downloader(advertise=8192, deliver=8192)
        returned = dl.download_installer("1.20.1", "47.1.0", self.target)
        self.assertEqual(Path(returned), self.target)
        self.assertTrue(self.target.exists())
        self.assertEqual(self.target.stat().st_size, 8192)

    def test_truncated_download_raises(self):
        # A dropped connection: 4 KB of an advertised 4 MB.
        dl = self._downloader(advertise=4_000_000, deliver=4096)
        with self.assertRaises(RuntimeError) as ctx:
            dl.download_installer("1.20.1", "47.1.0", self.target)
        self.assertIn("Truncated download", str(ctx.exception))

    def test_truncated_download_leaves_no_jar_behind(self):
        # install_forge() runs `java -jar` on whatever sits at this path.
        dl = self._downloader(advertise=4_000_000, deliver=4096)
        with self.assertRaises(RuntimeError):
            dl.download_installer("1.20.1", "47.1.0", self.target)
        self.assertFalse(self.target.exists(),
                         "a partial installer must not be left at the target")
        leftovers = list(self.tmp.rglob("*.jar")) + list(self.tmp.rglob("*.tmp"))
        self.assertEqual(leftovers, [], "temp files must be cleaned up")

    def test_no_content_length_still_writes(self):
        # A chunked response advertises nothing; there is nothing to check.
        dl = self._downloader(advertise=0, deliver=4096)
        dl.download_installer("1.20.1", "47.1.0", self.target)
        self.assertEqual(self.target.stat().st_size, 4096)


if __name__ == "__main__":
    unittest.main(verbosity=2)
