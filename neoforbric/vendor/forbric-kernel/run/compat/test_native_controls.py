"""native-controls.py run-set: how one server console is classified, and what identifies a mod set."""
import importlib.util
import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

_spec = importlib.util.spec_from_file_location("native_controls", Path(__file__).with_name("native-controls.py"))
nc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(nc)

BOOT = "[12:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.5\n[12:00:09] [Server thread/INFO]: Starting minecraft server version 26.2\n"
DONE = '[12:00:20] [Server thread/INFO]: Done (11.204s)! For help, type "help"\n'
TICKED = "[12:00:21] [Server thread/INFO]: The game time is 31 tick(s)\n[12:00:33] [Server thread/INFO]: The game time is 262 tick(s)\n"
STOPPED = "[12:00:33] [Server thread/INFO]: Stopping the server\n[12:00:33] [Server thread/INFO]: Stopping server\n"


class ServerOutcome(unittest.TestCase):
    def test_a_server_that_ticked_and_stopped_is_done(self):
        self.assertEqual(nc.DONE, nc.server_outcome(BOOT + DONE + TICKED + STOPPED, 200, 0))

    def test_done_followed_by_an_exception_is_a_crash(self):
        log = BOOT + DONE + "[12:00:22] [Server thread/INFO]: The game time is 40 tick(s)\n" \
            'Exception in thread "Server thread" java.lang.IllegalStateException: Not building!\n' \
            "\tat net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:1)\n"
        self.assertEqual(nc.CRASH, nc.server_outcome(log, 200, 1))
        self.assertEqual(nc.CRASH, nc.server_outcome(log, 200, None))

    def test_a_crash_report_after_done_is_a_crash_even_when_the_server_then_stops(self):
        log = BOOT + DONE + TICKED + "---- Minecraft Crash Report ----\n// Oops.\n\nDescription: Exception ticking world\n" + STOPPED
        self.assertEqual(nc.CRASH, nc.server_outcome(log, 200, 0))

    def test_an_exception_a_mod_logs_and_survives_is_not_a_crash(self):
        caught = ("[12:00:21] [Server thread/WARN]: Failed to load something optional\n"
                  "java.lang.NullPointerException: Cannot invoke \"Object.toString()\" because \"x\" is null\n"
                  "\tat example.Mod.onStart(Mod.java:3)\n")
        self.assertEqual(nc.DONE, nc.server_outcome(BOOT + DONE + caught + TICKED + STOPPED, 200, 0))

    def test_a_worker_thread_dying_is_evidence_not_an_outcome(self):
        worker = 'Exception in thread "Update Checker" java.net.UnknownHostException: example.invalid\n'
        log = BOOT + DONE + worker + TICKED + STOPPED
        self.assertEqual(nc.DONE, nc.server_outcome(log, 200, 0))
        self.assertEqual(['Exception in thread "Update Checker"'], nc.other_thread_failures(log))

    def test_a_server_that_never_reached_its_ticks_stalled(self):
        log = BOOT + DONE + "[12:00:21] [Server thread/INFO]: The game time is 31 tick(s)\n"
        self.assertEqual(nc.STALL, nc.server_outcome(log, 200, None))

    def test_a_jvm_kept_alive_after_a_clean_stop_is_still_done(self):
        # The runner kills it (exit_code None); lingeredAfterStop records that separately.
        self.assertEqual(nc.DONE, nc.server_outcome(BOOT + DONE + TICKED + STOPPED, 200, None))

    def test_a_jvm_that_vanished_after_done_crashed(self):
        self.assertEqual(nc.CRASH, nc.server_outcome(BOOT + DONE + "[12:00:21] [Server thread/INFO]: The game time is 31 tick(s)\n", 200, 134))

    def test_no_done_and_a_start_failure_failed_to_start(self):
        log = BOOT + "[12:00:10] [Server thread/ERROR]: Failed to start the minecraft server\njava.lang.RuntimeException: boom\n"
        self.assertEqual(nc.FAILED_TO_START, nc.server_outcome(log, 200, 1))
        self.assertEqual(nc.FAILED_TO_START, nc.server_outcome(log, 200, None))

    def test_an_uncaught_main_thread_exception_before_done_failed_to_start(self):
        log = BOOT + 'Exception in thread "main" net.fabricmc.loader.impl.FormattedException: Some of your mods are incompatible\n'
        self.assertEqual(nc.FAILED_TO_START, nc.server_outcome(log, 200, 1))

    def test_a_forbric_policy_stop_failed_to_start(self):
        log = BOOT + "[Forbric/Compatibility] launch stopped: required mod initialization or features are unavailable\n"
        self.assertEqual(nc.FAILED_TO_START, nc.server_outcome(log, 200, 78))

    def test_an_exit_without_done_or_marker_failed_to_start(self):
        self.assertEqual(nc.FAILED_TO_START, nc.server_outcome(BOOT, 200, 1))

    def test_a_killed_server_that_never_said_anything_stalled(self):
        self.assertEqual(nc.STALL, nc.server_outcome(BOOT, 200, None))

    def test_zero_ticks_needs_only_done_and_stop(self):
        self.assertEqual(nc.DONE, nc.server_outcome(BOOT + DONE + STOPPED, 0, 0))

    def test_every_answer_is_a_declared_outcome(self):
        logs = [BOOT, BOOT + DONE, BOOT + DONE + TICKED + STOPPED]
        for log in logs:
            for code in (None, 0, 1, 78):
                self.assertIn(nc.server_outcome(log, 200, code), nc.SERVER_OUTCOMES)


class RunSetCommandLine(unittest.TestCase):
    def test_the_policy_reaches_the_forbric_arm(self):
        seen = {}

        def fake(engine, family, mods, ticks, timeout, xmx, level_type, policy, keep):
            seen.update(engine=engine, mods=mods, ticks=ticks, policy=policy)
            return {"engine": engine, "outcome": nc.DONE, "signature": None, "modSetSha256": "x", "jars": len(mods), "seconds": 1.0, "result": "r"}
        with mock.patch.object(nc, "run_set", fake), mock.patch.object(sys, "argv", ["native-controls.py", "run-set", "--engine", "forbric",
                                                                                     "--policy", "continue", "--ticks", "40", "--mods", "a.jar", "b.jar"]), \
                mock.patch("sys.stdout", new_callable=io.StringIO):
            self.assertEqual(0, nc.main())
        self.assertEqual(dict(engine="forbric", mods=["a.jar", "b.jar"], ticks=40, policy="continue"), seen)


class ModSet(unittest.TestCase):
    def test_the_set_digest_is_the_bytes_not_the_names_or_order(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "a.jar").write_bytes(b"one"); (tmp / "b.jar").write_bytes(b"two")
            (tmp / "renamed.jar").write_bytes(b"one"); (tmp / "c.jar").write_bytes(b"three")
            rows, first = nc.mod_set([tmp / "b.jar", tmp / "a.jar"])
            self.assertEqual(["a.jar", "b.jar"], [row["name"] for row in rows])
            self.assertEqual(first, nc.mod_set([tmp / "renamed.jar", tmp / "b.jar"])[1])
            self.assertNotEqual(first, nc.mod_set([tmp / "a.jar", tmp / "c.jar"])[1])
            self.assertTrue(all("path" not in row for row in rows))


if __name__ == "__main__":
    unittest.main()
