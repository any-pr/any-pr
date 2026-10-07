import ast
import itertools
from pathlib import Path
import unittest
from sweep_verdict import bad_rows, classify_run, missing_subjects, mod_status, pack_strict, subject_strict

HERE = Path(__file__).resolve().parent
OK = {'modId': 'alpha', 'jar': 'alpha-1.0.jar', 'bundledBy': '', 'status': 'OK'}
NESTED = {'modId': 'alpha-lib', 'jar': 'alpha-lib-0.3.jar', 'bundledBy': 'alpha', 'status': 'DEGRADED'}
OTHER = {'modId': 'beta', 'jar': 'beta-2.0.jar', 'bundledBy': '', 'status': 'FAILED'}


# The code per-mod.py and mixed.py ran before the extraction, verbatim, so the grids below can say the module decides
# exactly as they did.
def classify_before(driver, console, crashes):
    if crashes or 'Game crashed' in console or 'Preparing crash report' in console:
        return 'CRASH'
    if 'PASS client' in driver:
        return 'PASS'
    if 'stopped producing output' in driver:
        return 'STALL' if 'joined world via quick-play' not in console else 'STALL_IN_WORLD'
    if 'joined world via quick-play' not in console:
        return 'NO_WORLD'
    if 'drew=False' in driver:
        return 'NOT_DRAWN'
    return 'FAIL'


def mod_status_before(jar, report):
    rows = [m for m in report.get('mods', []) if m.get('jar') == jar]
    own = {m['modId'] for m in rows}
    rows += [m for m in report.get('mods', []) if m.get('bundledBy') in own and m not in rows]
    if not rows:
        return 'ABSENT', []
    worst = 'FAILED' if any(m['status'] == 'FAILED' for m in rows) else \
            'DEGRADED' if any(m['status'] != 'OK' for m in rows) else 'OK'
    return worst, [f"{m['modId']}={m['status']}" for m in rows if m['status'] != 'OK']


def per_mod_strict_before(run, status, report, unresolved):
    dependency_issues = [m['modId'] + '=' + m['status'] for m in report.get('mods', []) if m.get('status') != 'OK']
    return (run == 'PASS' and status == 'OK' and bool(report.get('mods')) and
            not dependency_issues and not unresolved and not report.get('catalogFailures') and
            report.get('confirmedRequired', 0) == 0)


def mixed_strict_before(returncode, verdict, report, subjects, saved):
    bad = [row for row in report.get('mods', []) if row.get('status') != 'OK']
    missing = [name for name in subjects if mod_status_before(name, report)[0] != 'OK']
    return returncode == 0 and verdict == 'PASS' and bool(report.get('mods')) and not bad and not missing and saved and not report.get('catalogFailures') and report.get('confirmedRequired', 0) == 0


def reports():
    """Every combination of rows, catalog failures and confirmed-required counts the predicates read."""
    for rows, catalog, confirmed in itertools.product(
            [None, [], [OK], [OK, NESTED], [OK, OTHER], [dict(OK, status='DEGRADED')]],
            [None, [], ['beta-2.0.jar: unreadable']], [None, 0, 2]):
        report = {}
        if rows is not None:
            report['mods'] = rows
        if catalog is not None:
            report['catalogFailures'] = catalog
        if confirmed is not None:
            report['confirmedRequired'] = confirmed
        yield report


class ClassifyTest(unittest.TestCase):
    def test_each_outcome(self):
        joined = '[Render thread/INFO]: joined world via quick-play\n'
        self.assertEqual('CRASH', classify_run('PASS client exit=0', joined, ['crash-2026-10-01_21.00.12-client.txt']))
        self.assertEqual('CRASH', classify_run('PASS client exit=0', joined + 'Game crashed!', []))
        self.assertEqual('CRASH', classify_run('', 'Preparing crash report with UUID', []))
        self.assertEqual('PASS', classify_run('PASS client exit=0 joined=True drew=True', joined, []))
        self.assertEqual('STALL', classify_run('FAIL client stopped producing output 121s ago', '', []))
        self.assertEqual('STALL_IN_WORLD', classify_run('FAIL client stopped producing output 121s ago', joined, []))
        self.assertEqual('NO_WORLD', classify_run('FAIL client exit=1 joined=False drew=False', '', []))
        self.assertEqual('NOT_DRAWN', classify_run('FAIL client exit=0 joined=True drew=False', joined, []))
        self.assertEqual('FAIL', classify_run('FAIL client remained alive after grace', joined, []))

    def test_unchanged_from_per_mod(self):
        markers = ['PASS client', 'stopped producing output', 'drew=False', 'drew=True']
        console_markers = ['Game crashed', 'Preparing crash report', 'joined world via quick-play']
        for driver_bits in itertools.product([False, True], repeat=len(markers)):
            driver = ' '.join(marker for marker, on in zip(markers, driver_bits) if on)
            for console_bits in itertools.product([False, True], repeat=len(console_markers)):
                console = '\n'.join(marker for marker, on in zip(console_markers, console_bits) if on)
                for crashes in ([], ['crash.txt']):
                    self.assertEqual(classify_before(driver, console, crashes), classify_run(driver, console, crashes),
                                     (driver, console, crashes))


class StatusTest(unittest.TestCase):
    def test_a_jar_is_as_bad_as_the_worst_row_it_owns_or_bundles(self):
        self.assertEqual(('OK', []), mod_status('alpha-1.0.jar', {'mods': [OK, OTHER]}))
        self.assertEqual(('DEGRADED', ['alpha-lib=DEGRADED']), mod_status('alpha-1.0.jar', {'mods': [OK, NESTED, OTHER]}))
        self.assertEqual(('FAILED', ['beta=FAILED']), mod_status('beta-2.0.jar', {'mods': [OK, OTHER]}))
        self.assertEqual(('ABSENT', []), mod_status('gamma.jar', {'mods': [OK]}))
        self.assertEqual(('ABSENT', []), mod_status('alpha-1.0.jar', {}))

    def test_bad_rows_and_missing_subjects(self):
        report = {'mods': [OK, NESTED, OTHER]}
        self.assertEqual([NESTED, OTHER], bad_rows(report))
        self.assertEqual(['alpha-1.0.jar', 'beta-2.0.jar', 'gamma.jar'],
                         missing_subjects(['alpha-1.0.jar', 'beta-2.0.jar', 'gamma.jar'], report))
        self.assertEqual([], missing_subjects(['alpha-1.0.jar'], {'mods': [OK]}))

    def test_unchanged_from_per_mod(self):
        for report in reports():
            for jar in ('alpha-1.0.jar', 'beta-2.0.jar', 'gamma.jar'):
                self.assertEqual(mod_status_before(jar, report), mod_status(jar, report), (jar, report))


class StrictTest(unittest.TestCase):
    def test_a_subject_passes_only_when_everything_is_clean(self):
        clean = {'mods': [OK], 'catalogFailures': [], 'confirmedRequired': 0}
        self.assertTrue(subject_strict('PASS', 'OK', clean, {}))
        self.assertFalse(subject_strict('PASS', 'OK', dict(clean, mods=[OK, OTHER]), {}))
        self.assertFalse(subject_strict('PASS', 'OK', clean, {'alpha-1.0.jar': ['id:gamma']}))
        self.assertFalse(subject_strict('PASS', 'OK', dict(clean, confirmedRequired=1), {}))
        self.assertFalse(subject_strict('PASS', 'OK', {'mods': []}, {}))
        self.assertFalse(subject_strict('NOT_DRAWN', 'OK', clean, {}))

    def test_a_pack_also_needs_exit_zero_every_subject_and_a_saved_world(self):
        clean = {'mods': [OK]}
        self.assertTrue(pack_strict(0, 'PASS', clean, [], True))
        self.assertFalse(pack_strict(1, 'PASS', clean, [], True))
        self.assertFalse(pack_strict(0, 'PASS', clean, ['gamma.jar'], True))
        self.assertFalse(pack_strict(0, 'PASS', clean, [], False))
        self.assertFalse(pack_strict(0, 'PASS', dict(clean, catalogFailures=['x']), [], True))

    def test_unchanged_from_per_mod_and_mixed(self):
        subjects_cases = [[], ['alpha-1.0.jar'], ['alpha-1.0.jar', 'gamma.jar']]
        for report in reports():
            for run, status, unresolved in itertools.product(['PASS', 'CRASH'], ['OK', 'DEGRADED'], [{}, {'a': ['x']}]):
                self.assertIs(per_mod_strict_before(run, status, report, unresolved),
                              subject_strict(run, status, report, unresolved), (run, status, report, unresolved))
            for returncode, run, subjects, saved in itertools.product([0, 1], ['PASS', 'NO_WORLD'], subjects_cases, [True, False]):
                missing = missing_subjects(subjects, report)
                self.assertIs(mixed_strict_before(returncode, run, report, subjects, saved),
                              pack_strict(returncode, run, report, missing, saved), (returncode, run, report, subjects, saved))


class OneDefinitionTest(unittest.TestCase):
    def test_the_sweep_scripts_decide_through_this_module(self):
        for script in ('per-mod.py', 'mixed.py'):
            tree = ast.parse((HERE / script).read_text(encoding='utf-8'))
            defined = {node.name for node in ast.walk(tree) if isinstance(node, ast.FunctionDef)}
            self.assertFalse(defined & {'classify_run', 'mod_status'}, script)
            imported = {node.module for node in ast.walk(tree) if isinstance(node, ast.ImportFrom)} | \
                {alias.name for node in ast.walk(tree) if isinstance(node, ast.Import) for alias in node.names}
            self.assertIn('sweep_verdict', imported, script)


if __name__ == '__main__':
    unittest.main()
