import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import time
import types
import unittest
from unittest import mock
import mixed

OK_ROW = {'modId': 'alpha', 'version': '1.0', 'ecosystem': 'FABRIC', 'jar': 'alpha-1.0.jar', 'bundledBy': '', 'status': 'OK'}
CRASH = ('---- Minecraft Crash Report ----\n\njava.lang.IllegalArgumentException: Multiple overrides for option '
         "'sodium:general.fullscreen_mode'! Sources: chloride and cwb\n")


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8')
    return path


class FakeDriver:
    """Stands in for run-client-test.py: writes what a session leaves in the instance, and records its arguments."""

    def __init__(self, crash=False, saved=True, rows=(OK_ROW,), analysis=None, driver=None):
        self.crash, self.saved, self.rows, self.analysis, self.driver = crash, saved, list(rows), analysis, driver
        self.calls = []

    def __call__(self, instance, log, ticks, jvm, stall, timeout, grace):
        self.calls.append(dict(instance=instance, ticks=ticks, jvm=jvm, stall=stall, timeout=timeout, grace=grace))
        console = '[Render thread/INFO]: joined world via quick-play\n'
        if self.crash:
            console += 'Game crashed! Crash report saved to: crash-reports/crash-2026-10-01_21.00.12-client.txt\n'
            write(instance / 'crash-reports' / 'crash-2026-10-01_21.00.12-client.txt', CRASH)
        if self.analysis is not None:
            write(instance / '.forbric-kernel' / 'crash-analysis.txt', self.analysis)
        write(instance / 'client-console.log', console)
        write(instance / '.forbric-kernel' / 'compatibility-report.json', json.dumps({'mods': self.rows}))
        write(instance / 'forbric-mods.txt', '# sodium = neoforge\n')
        if self.saved:
            world = instance / 'saves' / 'compat-world'
            write(world / 'level.dat', 'level')
            write(world / 'region' / 'r.0.0.mca', 'region')
        default = 'FAIL client exit=255 joined=False drew=False' if self.crash else 'PASS client exit=0 joined=True drew=True'
        write(log, (self.driver or default) + '\n')
        return 1 if self.crash else 0


class RunTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.instance = self.root / 'inst'
        self.out = self.root / 'out'
        self.instance.mkdir()
        # run() prints each session's result line, as it does for the person running the sweep.
        self.enterContext(contextlib.redirect_stdout(io.StringIO()))

    def test_importing_starts_nothing(self):
        # per-mod.py exits without FORBRIC_JAVA and FORBRIC_VERSION; importing mixed must not load it.
        self.assertIsNone(mixed._permod)

    def test_a_crashing_session_keeps_the_crash_analysis_and_the_arbitration(self):
        driver = FakeDriver(crash=True, saved=False, analysis='Forbric crash analysis\n')
        result = mixed.run('first', 200, ['alpha-1.0.jar'], self.out, jvm=['-Dforbric.modOwner=sodium=neoforge'],
                           stall=120, timeout=420, grace=20, instance=self.instance, launch=driver)
        evidence = self.out / 'first'
        self.assertEqual('CRASH', result['run'])
        self.assertFalse(result['strict'])
        for name in ('crash-analysis.txt', 'crash-2026-10-01_21.00.12-client.txt', 'forbric-mods.txt',
                     'compatibility-report.json', 'client-console.log', 'driver.log', 'result.json'):
            self.assertTrue((evidence / name).is_file(), name)
        self.assertEqual([evidence / 'crash-2026-10-01_21.00.12-client.txt'], mixed.crash_reports(evidence))
        self.assertEqual([dict(instance=self.instance, ticks=200, jvm=['-Dforbric.modOwner=sodium=neoforge'],
                               stall=120, timeout=420, grace=20)], driver.calls)
        self.assertEqual(result, json.loads((evidence / 'result.json').read_text()))

    def test_a_clean_session_is_strict_and_an_old_crash_analysis_is_left_behind(self):
        stale = write(self.instance / '.forbric-kernel' / 'crash-analysis.txt', 'from an earlier session\n')
        old = time.time() - 3600
        os.utime(stale, (old, old))
        result = mixed.run('clean', 200, ['alpha-1.0.jar'], self.out, instance=self.instance, launch=FakeDriver())
        self.assertEqual('PASS', result['run'])
        self.assertTrue(result['strict'], result)
        self.assertFalse((self.out / 'clean' / 'crash-analysis.txt').exists())

    def test_a_crash_stamped_by_a_lagging_filesystem_clock_still_counts(self):
        # Windows stamps files from a clock tick that can trail time.time(); an instant crash then carries an mtime a
        # few milliseconds before the session began. It is still this session's crash.
        class LaggingClock(FakeDriver):
            def __call__(self, instance, log, ticks, jvm, stall, timeout, grace):
                code = super().__call__(instance, log, ticks, jvm, stall, timeout, grace)
                lagged = time.time() - 0.05
                for written in instance.rglob('*'):
                    if written.is_file():
                        os.utime(written, (lagged, lagged))
                return code
        result = mixed.run('lagging', 200, ['alpha-1.0.jar'], self.out, instance=self.instance, launch=LaggingClock(crash=True))
        self.assertEqual('CRASH', result['run'])
        self.assertEqual([self.out / 'lagging' / 'crash-2026-10-01_21.00.12-client.txt'],
                         mixed.crash_reports(self.out / 'lagging'))

    def test_a_crash_analysis_alone_is_not_a_crash_report(self):
        result = mixed.run('analysis', 200, ['alpha-1.0.jar'], self.out, instance=self.instance,
                           launch=FakeDriver(analysis='Forbric crash analysis\n'))
        self.assertTrue((self.out / 'analysis' / 'crash-analysis.txt').is_file())
        self.assertEqual('PASS', result['run'])

    def test_subjects_are_explicit(self):
        result = mixed.run('missing', 200, ['alpha-1.0.jar', 'gamma-1.0.jar'], self.out, instance=self.instance,
                           launch=FakeDriver())
        self.assertEqual(['gamma-1.0.jar'], result['missing_subjects'])
        self.assertFalse(result['strict'])

    def test_the_driver_gets_the_flags_and_the_timeouts(self):
        self.addCleanup(setattr, mixed, '_permod', None)
        mixed._permod = types.SimpleNamespace(MC=self.root / 'mc', VERSION='kernel-profile', JAVA='/opt/java/bin/java')
        with mock.patch.object(mixed.subprocess, 'run', return_value=types.SimpleNamespace(returncode=3)) as launched:
            code = mixed.drive(self.instance, self.root / 'driver.log', 200, ['-Da=1', '-Db=2'], 120, 420, 20)
        self.assertEqual(3, code)
        command, env = launched.call_args.args[0], launched.call_args.kwargs['env']
        self.assertEqual(['run-client-test.py', '--jvm=-Dforbric.compatibilityPolicy=strict', '--jvm=-Da=1', '--jvm=-Db=2'],
                         command[2:])
        self.assertTrue(command[1].endswith('mac-run.py'))
        self.assertEqual(dict(SWEEP_WORLD_TICKS='200', CLIENT_STALL='120', RUN_TIMEOUT='420', GRACE='20',
                              FORBRIC_INSTANCE=str(self.instance), FORBRIC_VERSION='kernel-profile',
                              FORBRIC_JAVA='/opt/java/bin/java', FORBRIC_MC=str(self.root / 'mc')),
                         {key: env[key] for key in ('SWEEP_WORLD_TICKS', 'CLIENT_STALL', 'RUN_TIMEOUT', 'GRACE',
                                                    'FORBRIC_INSTANCE', 'FORBRIC_VERSION', 'FORBRIC_JAVA', 'FORBRIC_MC')})

    def test_a_label_is_never_reused(self):
        mixed.run('once', 200, [], self.out, instance=self.instance, launch=FakeDriver())
        with self.assertRaises(FileExistsError):
            mixed.run('once', 200, [], self.out, instance=self.instance, launch=FakeDriver())


class SelectTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.data = Path(temporary.name)
        self.jars = {'a-1.0.jar': b'alpha', 'b-1.0.jar': b'beta', 'lib-2.0.jar': b'library', 'c-1.0.jar': b'gamma'}
        for name, data in self.jars.items():
            (self.data / 'mods').mkdir(exist_ok=True)
            (self.data / 'mods' / name).write_bytes(data)
        rows = [self.row('a-1.0.jar', 'popular'), self.row('b-1.0.jar', 'random'), self.row('lib-2.0.jar', 'dep'),
                self.row('c-1.0.jar', 'random')]
        (self.data / 'manifest.json').write_text(json.dumps(rows))
        (self.data / 'closure.json').write_text(json.dumps({'a-1.0.jar': ['lib-2.0.jar'], 'b-1.0.jar': [],
                                                            'lib-2.0.jar': [], 'c-1.0.jar': []}))
        self.results = self.data / 'results.jsonl'

    def row(self, name, kind):
        data = self.jars[name]
        return dict(filename=name, kind=kind, sha1=hashlib.sha1(data).hexdigest(), size=len(data))

    def sweep(self, *rows):
        self.results.write_text(''.join(json.dumps(row) + '\n' for row in rows))

    def result(self, jar, strict, kernel='k1', deps=()):
        return dict(jar=jar, strict=strict, kernel_sha256=kernel,
                    input_sha256={name: hashlib.sha256(self.jars[name]).hexdigest() for name in (jar,) + tuple(deps)})

    def test_strict_combines_the_strict_subjects_and_their_dependencies(self):
        self.sweep(self.result('a-1.0.jar', True, deps=['lib-2.0.jar']), self.result('b-1.0.jar', False),
                   self.result('c-1.0.jar', True))
        subjects, jars, inputs, rows = mixed.select_pack(self.data, 'strict', 'k1', self.results)
        self.assertEqual(['a-1.0.jar', 'c-1.0.jar'], subjects)
        self.assertEqual(['a-1.0.jar', 'c-1.0.jar', 'lib-2.0.jar'], jars)
        self.assertEqual(hashlib.sha256(b'library').hexdigest(), inputs['lib-2.0.jar'])
        self.assertEqual(['a-1.0.jar', 'lib-2.0.jar', 'c-1.0.jar'], [row['filename'] for row in rows])

    def test_strict_refuses_an_incomplete_stale_or_changed_sweep(self):
        self.sweep(self.result('a-1.0.jar', True), self.result('b-1.0.jar', True))
        with self.assertRaisesRegex(SystemExit, 'incomplete'):
            mixed.select_pack(self.data, 'strict', 'k1', self.results)
        self.sweep(self.result('a-1.0.jar', True), self.result('b-1.0.jar', True), self.result('c-1.0.jar', True, kernel='k0'))
        with self.assertRaisesRegex(SystemExit, 'different kernel: c-1.0.jar'):
            mixed.select_pack(self.data, 'strict', 'k1', self.results)
        self.sweep(self.result('a-1.0.jar', True, deps=['lib-2.0.jar']), self.result('b-1.0.jar', True),
                   self.result('c-1.0.jar', True))
        (self.data / 'mods' / 'lib-2.0.jar').write_bytes(b'rebuilt')
        with self.assertRaisesRegex(SystemExit, 'Test input changed: lib-2.0.jar'):
            mixed.select_pack(self.data, 'strict', 'k1', self.results)

    def test_all_needs_no_individual_results(self):
        subjects, jars, _, _ = mixed.select_pack(self.data, 'all', 'k1', self.data / 'never-written.jsonl')
        self.assertEqual(['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar'], subjects)
        self.assertEqual(['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'lib-2.0.jar'], jars)

    def test_all_checks_every_jar_against_the_manifest(self):
        (self.data / 'mods' / 'lib-2.0.jar').write_bytes(b'librarx')
        with self.assertRaisesRegex(SystemExit, 'lib-2.0.jar: sha1 differs'):
            mixed.select_pack(self.data, 'all', 'k1', self.results)

    def test_a_manifest_sha256_wins_and_an_undigested_or_unlisted_jar_is_refused(self):
        self.assertIsNone(mixed.manifest_mismatch({'sha256': hashlib.sha256(b'beta').hexdigest(), 'sha1': 'stale'},
                                                  self.data / 'mods' / 'b-1.0.jar'))
        self.assertEqual('sha256 differs from the manifest',
                         mixed.manifest_mismatch({'sha256': '0' * 64}, self.data / 'mods' / 'b-1.0.jar'))
        self.assertEqual('size differs from the manifest',
                         mixed.manifest_mismatch({'sha1': hashlib.sha1(b'beta').hexdigest(), 'size': 5},
                                                 self.data / 'mods' / 'b-1.0.jar'))
        self.assertEqual('the manifest records no digest for it', mixed.manifest_mismatch({}, self.data / 'mods' / 'b-1.0.jar'))
        self.assertEqual('not in the manifest', mixed.manifest_mismatch(None, self.data / 'mods' / 'b-1.0.jar'))

    def test_the_mode_is_on_the_command_line(self):
        with self.assertRaises(SystemExit) as refused, contextlib.redirect_stderr(io.StringIO()):
            mixed.main(['--subjects', 'some'])
        self.assertEqual(2, refused.exception.code)


if __name__ == '__main__':
    unittest.main()
