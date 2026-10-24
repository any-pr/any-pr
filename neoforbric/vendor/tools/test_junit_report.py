import contextlib
import io
from pathlib import Path
import tempfile
import unittest

import junit_report

SUITE = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="{cls}" tests="{tests}" skipped="{skipped}" failures="{failures}" errors="0">
{cases}
</testsuite>
'''


def case(cls, name, body=''):
    return f'  <testcase name="{name}" classname="{cls}" time="0.0">{body}</testcase>'


def skipped(message=None):
    return '<skipped/>' if message is None else (
        f'<skipped message="{message}" type="org.opentest4j.TestAbortedException">trace</skipped>')


class JUnitReportTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='forbric junit ')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.results = self.root / 'test-results'

    def suite(self, suite, cls, cases, tests, skipped_count, failures=0):
        directory = self.results / suite
        directory.mkdir(parents=True, exist_ok=True)
        (directory / f'TEST-{cls}.xml').write_text(SUITE.format(
            cls=cls, tests=tests, skipped=skipped_count, failures=failures, cases='\n'.join(cases)), encoding='utf-8')

    def standard(self):
        checkout = '/home/runner/work/forbric'
        self.suite('test', 'a.SkipsTest', [
            case('a.SkipsTest', 'staged()', skipped(f'Assumption failed: staged jar absent at {checkout}/x.jar')),
            case('a.SkipsTest', 'runs()'),
        ], 2, 1)
        self.suite('test', 'a.ClassAbortTest', [case('a.ClassAbortTest', 'one()', skipped())], 1, 1)
        return checkout

    def ratchet(self, *extra):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = junit_report.main(['ratchet', '--profile', 'ci-unstaged', '--results', str(self.results),
                                      '--executed', 'test', '--baseline', str(self.root / 'base.tsv'),
                                      '--actual', str(self.root / 'actual.tsv'), '--root', '/home/runner/work/forbric',
                                      *extra])
        return code, out.getvalue()

    def test_a_skip_is_read_with_its_message_and_the_checkout_path_is_normalised(self):
        checkout = self.standard()
        rows = junit_report.actual_lines(self.results, ['test'], {'test'}, checkout)
        self.assertIn(('test', 'a.SkipsTest', 'staged()', 'staged jar absent at $ROOT/x.jar'), rows)
        self.assertIn(('test', 'a.ClassAbortTest', 'one()', junit_report.CLASS_LEVEL), rows)
        self.assertNotIn('runs()', [r[2] for r in rows])

    def test_a_suite_that_did_not_execute_is_its_own_line(self):
        self.standard()
        rows = junit_report.actual_lines(self.results, ['test', 'transferTest'], {'test'})
        self.assertIn(('transferTest',) + junit_report.NOT_EXECUTED, rows)
        # Stale XML from an earlier invocation is not evidence that the suite ran in this one.
        rows = junit_report.actual_lines(self.results, ['test'], set())
        self.assertEqual(rows, [('test',) + junit_report.NOT_EXECUTED])

    def test_ratchet_reports_exactly_the_new_and_the_stale_ids(self):
        base = [('test', 'a.T', 'old()', 'r'), ('test', 'a.T', 'kept()', 'r')]
        actual = [('test', 'a.T', 'kept()', 'a different reason'), ('test', 'a.T', 'fresh()', 'r')]
        new, stale = junit_report.ratchet(actual, base)
        self.assertEqual(new, [('test', 'a.T', 'fresh()', 'r')])
        self.assertEqual(stale, [('test', 'a.T', 'old()', 'r')])

    def test_ratchet_command_bootstraps_matches_and_then_catches_a_new_skip(self):
        self.standard()
        code, out = self.ratchet()
        self.assertEqual(code, 1, out)
        self.assertIn('does not exist', out)
        self.assertTrue((self.root / 'actual.tsv').is_file())

        self.assertEqual(self.ratchet('--write')[0], 0)
        code, out = self.ratchet()
        self.assertEqual(code, 0, out)
        self.assertIn('unchanged', out)

        self.suite('test', 'a.NewTest', [case('a.NewTest', 'hides()', skipped('Assumption failed: no fixture'))], 1, 1)
        code, out = self.ratchet()
        self.assertEqual(code, 1, out)
        self.assertIn('+ test\ta.NewTest\thides()\tno fixture', out)

    def test_ratchet_command_fails_when_an_allowed_skip_starts_running(self):
        self.standard()
        self.ratchet('--write')
        (self.results / 'test' / 'TEST-a.ClassAbortTest.xml').unlink()
        self.suite('test', 'a.ClassAbortTest', [case('a.ClassAbortTest', 'one()')], 1, 0)
        code, out = self.ratchet()
        self.assertEqual(code, 1, out)
        self.assertIn('- test\ta.ClassAbortTest\tone()', out)

    def test_baseline_round_trip_keeps_comments_out_and_tabs_in_reasons(self):
        rows = [('test', 'a.T', 'x()', 'reason with\ttab')]
        junit_report.write_lines(self.root / 'b.tsv', rows, 'p')
        self.assertTrue((self.root / 'b.tsv').read_text(encoding='utf-8').startswith('#'))
        self.assertEqual(junit_report.read_baseline(self.root / 'b.tsv'), rows)

    def test_summary_counts_skips_names_failures_and_missing_suites(self):
        checkout = self.standard()
        self.suite('test', 'a.BrokenTest', [case('a.BrokenTest', 'boom()',
                                                 '<failure message="secret bytes" type="java.lang.AssertionError">x</failure>')], 1, 0, 1)
        text = junit_report.render_summary('Kernel', self.results, ['test', 'transferTest'], checkout)
        self.assertIn('| test | 4 | 2 | 2 | 50.0 | 1 |', text)
        self.assertIn('Did not execute (no results): `transferTest`', text)
        self.assertIn('a.BrokenTest.boom() (java.lang.AssertionError)', text)
        self.assertNotIn('secret bytes', text)
        self.assertIn('| 1 | staged jar absent at $ROOT/x.jar |', text)

    def test_normalize_strips_the_junit_prefix_and_collapses_whitespace(self):
        self.assertEqual(junit_report.normalize('Assumption failed: a\n  b', None, None), 'a b')
        self.assertEqual(junit_report.normalize('', None, None), junit_report.CLASS_LEVEL)


if __name__ == '__main__':
    unittest.main()
