"""Regression tests for the launch form parsing.

Run from anywhere:

    python mc-launcher/tests/test_ui_actions.py

``ui_actions`` imports PyQt6 and (through ``workers``) ``requests`` at
module scope, neither of which is installed in a headless checkout. When
they are missing, inert stand-ins are registered before the import so the
parsing logic -- which is pure Python -- can still be exercised. When they
are present, the real packages are used and the stubs are skipped.
"""

import importlib.util
import sys
import types
import unittest
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


def _ensure_importable():
    if importlib.util.find_spec("requests") is None:
        _stub("requests", Session=type("Session", (), {"__init__":
                                                      lambda self: setattr(self, "headers", {})}),
              HTTPError=Exception)

    if importlib.util.find_spec("PyQt6") is None:
        pyqt6 = _stub("PyQt6")
        _stub("PyQt6.QtCore",
              QObject=type("QObject", (), {"__init__": lambda self, *a, **k: None}),
              pyqtSignal=lambda *a: None)
        _stub("PyQt6.QtWidgets", QMessageBox=object)
        pyqt6.QtCore = sys.modules["PyQt6.QtCore"]
        pyqt6.QtWidgets = sys.modules["PyQt6.QtWidgets"]


_ensure_importable()

from mc_launcher.ui_actions import MainWindowActionsMixin  # noqa: E402


class LineEdit:
    def __init__(self, text):
        self._text = text

    def text(self):
        return self._text


class Combo:
    def currentData(self):
        return {"id": "1.20.1"}


class Button:
    def __init__(self):
        self.enabled = None

    def setEnabled(self, value):
        self.enabled = value


class MemoryWindow(MainWindowActionsMixin):
    """Only what _read_memory_mb() touches."""

    def __init__(self, text):
        self.memory_input = LineEdit(text)
        self.messages = []

    def log(self, message):
        self.messages.append(message)


class LaunchWindow(MainWindowActionsMixin):
    """Everything on_launch() touches before it starts a thread."""

    def __init__(self, memory, mc_dir="C:/nonexistent/.minecraft"):
        self.mc_dir_input = LineEdit(mc_dir)
        self.java_input = LineEdit("C:/nonexistent/java.exe")
        self.username_input = LineEdit("Player")
        self.memory_input = LineEdit(memory)
        self.version_combo = Combo()
        self.launch_btn = Button()
        self.download_btn = Button()
        self.forge_btn = Button()
        self.fabric_btn = Button()
        self.stop_btn = Button()
        self.worker = None
        self.messages = []

    def log(self, message):
        self.messages.append(message)


class ReadMemoryTests(unittest.TestCase):
    def test_valid_values(self):
        for raw, want in [("8192", 8192), (" 4096 ", 4096), ("512", 512)]:
            self.assertEqual(MemoryWindow(raw)._read_memory_mb(), want, raw)

    def test_empty_falls_back_to_the_default(self):
        self.assertEqual(MemoryWindow("")._read_memory_mb(), 2048)

    def test_non_numeric_is_rejected(self):
        for raw in ["abc", "8G", "1024.5", "1e3", "0x10"]:
            window = MemoryWindow(raw)
            self.assertIsNone(window._read_memory_mb(), raw)
            self.assertTrue(window.messages, "{} must be reported".format(raw))

    def test_non_positive_is_rejected(self):
        for raw in ["0", "-1", "-8192"]:
            window = MemoryWindow(raw)
            self.assertIsNone(window._read_memory_mb(), raw)
            self.assertTrue(window.messages, "{} must be reported".format(raw))


class OnLaunchTests(unittest.TestCase):
    def test_bad_memory_does_not_raise(self):
        # int() used to be called inline here, so these escaped the slot.
        for raw in ["abc", "8G", "1024.5", "0", "-1"]:
            window = LaunchWindow(raw)
            window.on_launch()
            self.assertTrue(window.messages,
                            "{} must log a reason, not raise".format(raw))

    def test_bad_memory_leaves_the_buttons_alone(self):
        window = LaunchWindow("abc")
        window.on_launch()
        self.assertIsNone(window.launch_btn.enabled)
        self.assertIsNone(window.stop_btn.enabled)


if __name__ == "__main__":
    unittest.main(verbosity=2)
