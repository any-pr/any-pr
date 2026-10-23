import itertools
import math
from pathlib import Path
import random
import unittest
from ddmin_core import (FAIL, PASS, UNRESOLVED, NotReproduced, closed, ddmin, minimise, normalise, one_minimal,
                        outcome, seeds, signature)

DATA = Path(__file__).resolve().parent / 'testdata'
CHLORIDE = 'chloride-NEOFORGE-mc26.2-v1.8.1.jar'
CWB = 'cwb-4.1.0+26.2.jar'
SODIUM = 'sodium-neoforge-0.9.2+mc26.2.jar'
CLASH = "java.lang.IllegalArgumentException: Multiple overrides for option 'sodium:general.fullscreen_mode'! Sources: chloride and cwb"


def fixture(name):
    return (DATA / name).read_text(encoding='utf-8')


def pack(size, *named):
    """size jar names: the named ones spread through generic filler, so no culprit sits at an edge."""
    filler = [f'mod-{i:02}-1.0.jar' for i in range(size - len(named))]
    for offset, name in enumerate(named):
        filler.insert((offset + 1) * len(filler) // (len(named) + 1), name)
    return filler


class Recording:
    """A fake oracle that keeps every configuration it was asked about."""

    def __init__(self, verdict):
        self.verdict, self.calls = verdict, []

    def __call__(self, config):
        self.calls.append(list(config))
        return self.verdict(set(config))


def needs(*culprits):
    return lambda config: FAIL if set(culprits) <= set(config) else PASS


class DdminTest(unittest.TestCase):
    def test_one_culprit_among_88_is_found_within_the_logarithmic_budget(self):
        items = pack(88)
        budget = 2 * math.log2(88) + 4
        for culprit in items:
            oracle = Recording(needs(culprit))
            self.assertEqual([culprit], ddmin(items, oracle))
            self.assertLessEqual(len(oracle.calls), budget, culprit)

    def test_a_two_mod_interaction_comes_back_as_exactly_the_pair(self):
        items = pack(88, CHLORIDE, CWB)
        self.assertEqual({CHLORIDE, CWB}, set(ddmin(items, Recording(needs(CHLORIDE, CWB)))))
        # Every placement, including both in the same half, which no single halving can separate.
        items = pack(20)
        for a, b in itertools.combinations(items, 2):
            self.assertEqual({a, b}, set(ddmin(items, needs(a, b))), (a, b))

    def test_any_conjunction_is_found_exactly(self):
        rng = random.Random(39)
        for _ in range(200):
            items = pack(rng.randint(1, 60))
            culprits = rng.sample(items, rng.randint(1, min(4, len(items))))
            self.assertEqual(set(culprits), set(ddmin(items, needs(*culprits))), culprits)

    def test_unresolved_never_reduces(self):
        # cwb without chloride crashes too, but differently: taking that run for the failure would give {cwb}.
        items = pack(16, CHLORIDE, CWB)

        def verdict(config):
            if {CHLORIDE, CWB} <= config:
                return FAIL
            return UNRESOLVED if CWB in config else PASS
        self.assertEqual({CHLORIDE, CWB}, set(ddmin(items, Recording(verdict))))

    def test_a_set_whose_every_part_is_unresolved_does_not_shrink(self):
        items = pack(6)
        oracle = Recording(lambda config: FAIL if config == set(items) else UNRESOLVED)
        self.assertEqual(items, ddmin(items, oracle))
        self.assertNotIn([], oracle.calls)

    def test_complements_are_tested_only_after_every_subset(self):
        items = pack(8)
        oracle = Recording(needs(items[0], items[7]))
        ddmin(items, oracle)
        # At four parts: four subsets of two, then complements of six.
        sizes = [len(call) for call in oracle.calls]
        self.assertEqual([8, 4, 4, 2, 2, 2, 2, 6], sizes[:8])

    def test_a_failing_first_set_is_where_the_search_starts(self):
        items = pack(88, CHLORIDE, CWB)
        oracle = Recording(needs(CHLORIDE, CWB))
        self.assertEqual({CHLORIDE, CWB}, set(ddmin(items, oracle, first=[CWB, 'not-installed.jar', CHLORIDE])))
        self.assertEqual([CWB, CHLORIDE], oracle.calls[0])
        self.assertNotIn(len(items), [len(call) for call in oracle.calls])

    def test_a_passing_first_set_falls_back_to_the_whole_pack(self):
        items = pack(30, CHLORIDE, CWB)
        oracle = Recording(needs(CHLORIDE, CWB))
        self.assertEqual({CHLORIDE, CWB}, set(ddmin(items, oracle, first=[CHLORIDE])))
        self.assertEqual([[CHLORIDE], items], oracle.calls[:2])

    def test_a_pack_that_does_not_fail_is_refused(self):
        with self.assertRaises(NotReproduced) as refused:
            ddmin(pack(10), lambda config: UNRESOLVED)
        self.assertEqual(UNRESOLVED, refused.exception.outcome)

    def test_an_oracle_must_answer_with_an_outcome(self):
        with self.assertRaisesRegex(ValueError, 'oracle returned True'):
            ddmin(pack(4), lambda config: True)

    def test_one_minimal_removes_single_items_until_none_can_go(self):
        items = pack(10, CHLORIDE, CWB)
        self.assertEqual([CHLORIDE, CWB], one_minimal(items, needs(CHLORIDE, CWB)))
        # A failure none of the items causes is reported as such, not pinned on the last one standing.
        self.assertEqual([], one_minimal(items, lambda config: FAIL))
        with self.assertRaises(NotReproduced):
            one_minimal(items, lambda config: PASS)


class ClosureTest(unittest.TestCase):
    def test_closed_adds_transitive_dependencies_once(self):
        closure = {'a.jar': ['lib.jar'], 'lib.jar': ['core.jar'], 'b.jar': ['core.jar']}
        self.assertEqual(['a.jar', 'b.jar', 'core.jar', 'lib.jar'], closed(['a.jar', 'b.jar'], closure))
        self.assertEqual(['lib.jar', 'a.jar', 'core.jar'], closed(['lib.jar', 'a.jar'], closure))
        self.assertEqual(['x.jar'], closed(['x.jar'], closure))

    def test_every_run_with_chloride_also_has_sodium_and_the_result_says_so(self):
        # Without Sodium there is no config API to refuse the pair, so chloride and cwb alone do not crash.
        for items in (pack(88, CHLORIDE, CWB), pack(88, CHLORIDE, SODIUM, CWB)):
            oracle = Recording(needs(CHLORIDE, CWB, SODIUM))
            result = minimise(items, oracle, closure={CHLORIDE: [SODIUM]})
            self.assertEqual({CHLORIDE, CWB}, set(result.minimal))
            self.assertEqual({CHLORIDE, CWB, SODIUM}, set(result.closed))
            for call in oracle.calls:
                if CHLORIDE in call:
                    self.assertIn(SODIUM, call)
            self.assertEqual(len(oracle.calls), result.calls)

    def test_subsets_that_close_to_the_same_launch_run_once(self):
        # [chloride] at three parts closes to [chloride, sodium], which was already run at two parts.
        oracle = Recording(needs(CHLORIDE, CWB, SODIUM))
        result = minimise([CHLORIDE, SODIUM, CWB], oracle, closure={CHLORIDE: [SODIUM]})
        launched = [frozenset(call) for call in oracle.calls]
        self.assertEqual(len(launched), len(set(launched)), oracle.calls)
        self.assertEqual({CHLORIDE, CWB}, set(result.minimal))


class SignatureTest(unittest.TestCase):
    def crash(self, header, *causes):
        lines = ['---- Minecraft Crash Report ----', '// fixture', '', 'Time: 2026-10-01 12:00:00',
                 'Description: Initializing game', '', header, '\tat forbric/a.B.c(B.java:1) ~[a.jar:?] {}']
        lines += [f'Caused by: {cause}' for cause in causes]
        return '\n'.join(lines + ['', '-' * 40, 'java.lang.Error: below the divider']) + '\n'

    def test_the_clash_from_the_fixture(self):
        self.assertEqual(CLASH, signature(crash_text=fixture('chloride-cwb-crash.txt'), exit_code=255))

    def test_sources_are_sorted_and_kept_whole(self):
        self.assertEqual(CLASH, signature(crash_text=self.crash(CLASH.replace('chloride and cwb', 'cwb and chloride'))))
        self.assertEqual('Overrides # Sources: mod1 and mod10 and mod2',
                         normalise('Overrides 3 Sources: mod2, mod10 and mod1'))

    def test_digits_hex_paths_and_timestamps_do_not_tell_two_runs_apart(self):
        one = ('java.lang.IllegalStateException: Failed to read /home/player/mc/config/a-17.toml at 2026-10-01 '
               '12:00:00 (Holder@1a2b3c4d, 0x7f3a, 4096 bytes, id 123e4567-e89b-12d3-a456-426614174000)')
        two = ('java.lang.IllegalStateException: Failed to read C:\\Users\\bob\\mc\\config\\a-17.toml at 2026-10-03 '
               '08:15:42 (Holder@5e6f7a8b, 0x1, 12 bytes, id 9b2f0c1d-0a1b-4c2d-8e3f-a0b1c2d3e4f5)')
        self.assertEqual(signature(crash_text=self.crash(one)), signature(crash_text=self.crash(two)))
        self.assertEqual('java.lang.IllegalStateException: Failed to read <path> at <time> (Holder<hex>, <hex>, # bytes, '
                         'id <hex>)', signature(crash_text=self.crash(one)))
        self.assertEqual('index # out of bounds for length #', normalise('index 17 out of bounds for length 4'))
        self.assertEqual(normalise('in handler$zca000$sodium$postInit'), normalise('in handler$bdf012$sodium$postInit'))

    def test_a_different_failure_has_a_different_signature(self):
        base = signature(crash_text=self.crash('java.lang.IllegalStateException: boom'))
        self.assertNotEqual(base, signature(crash_text=self.crash('java.lang.IllegalArgumentException: boom')))
        self.assertNotEqual(base, signature(crash_text=self.crash('java.lang.IllegalStateException: bang')))

    def test_the_top_of_the_chain_is_the_signature(self):
        text = self.crash('java.lang.RuntimeException: wrapper 1', 'java.lang.IllegalStateException: inner')
        self.assertEqual('java.lang.RuntimeException: wrapper #', signature(crash_text=text))
        self.assertEqual('java.lang.NoSuchFieldError', signature(crash_text=self.crash('java.lang.NoSuchFieldError')))

    def test_the_console_crash_report_wins_over_earlier_logged_exceptions(self):
        console = fixture('console-crash.txt')
        expected = ('java.lang.NullPointerException: Cannot invoke "net.minecraft.client.renderer.entity.EntityRenderer'
                    '.shouldRender(net.minecraft.world.entity.Entity)" because "renderer" is null')
        self.assertEqual(expected, signature(console_text=console, exit_code=255))
        self.assertEqual(expected, signature(console_text=console, crash_text='', exit_code=-1))

    def test_an_exception_that_escaped_a_thread_and_no_exception_at_all(self):
        console = '[main/INFO]: loading\nException in thread "main" java.lang.IllegalStateException: boom 42\n'
        self.assertEqual('java.lang.IllegalStateException: boom #', signature(console_text=console, exit_code=1))
        self.assertEqual('EXIT:1', signature(console_text='[main/INFO]: stalled\n', exit_code=1))

    def test_a_policy_stop_is_named_by_its_confirmed_required_findings(self):
        report = fixture('policy-stop-report.json')
        expected = 'POLICY_STOP:alpha:initialization:alpha,zeta:mixin:zeta.mixins.json:ZetaMixin'
        self.assertEqual(expected, signature(console_text='java.lang.Error: ignored\n', report_json_text=report, exit_code=78))
        self.assertEqual('POLICY_STOP:', signature(report_json_text='', exit_code=78))

    def test_outcome(self):
        self.assertEqual(FAIL, outcome(CLASH, CLASH))
        self.assertEqual(UNRESOLVED, outcome(CLASH, 'java.lang.NullPointerException'))
        self.assertEqual(PASS, outcome(CLASH, 'EXIT:0', passed=True))


class SeedTest(unittest.TestCase):
    def test_the_clash_seeds_strongest_evidence_first_and_ignores_the_system_report(self):
        found = seeds(fixture('chloride-cwb-crash.txt'), fixture('chloride-cwb-crash-analysis.txt'),
                      fixture('chloride-cwb-compatibility-report.json'), pack(88, CHLORIDE, CWB, SODIUM))
        # mod-07 appears only below the divider; the Sodium frame's jar is a nested one no row carries.
        self.assertEqual([CHLORIDE, CWB, SODIUM], found)

    def test_seeds_reach_the_pair_within_four_runs(self):
        items = pack(88, CHLORIDE, SODIUM, CWB)
        found = seeds(fixture('chloride-cwb-crash.txt'), fixture('chloride-cwb-crash-analysis.txt'),
                      fixture('chloride-cwb-compatibility-report.json'), items)
        oracle = Recording(needs(CHLORIDE, CWB, SODIUM))
        result = minimise(items, oracle, closure={CHLORIDE: [SODIUM]}, seed_jars=found)
        self.assertTrue(result.seeded)
        self.assertEqual({CHLORIDE, CWB}, set(result.minimal))
        self.assertLessEqual(len(oracle.calls), 4)

    def test_a_different_crash_is_unresolved_through_the_signature(self):
        # The real driver's oracle: compare each run's signature with the full pack's.
        items = pack(24, CHLORIDE, SODIUM, CWB)
        npe = 'java.lang.NullPointerException: Cannot invoke "Object.hashCode()" because "key" is null\n'

        def run(config):
            if {CHLORIDE, CWB, SODIUM} <= config:
                return fixture('chloride-cwb-crash.txt'), 255
            if CWB in config and SODIUM in config:
                return '---- Minecraft Crash Report ----\n' + npe, 255
            return '', 0
        reference = signature(crash_text=run(set(items))[0], exit_code=255)
        oracle = Recording(lambda config: outcome(reference, signature(crash_text=run(config)[0], exit_code=run(config)[1]),
                                                  passed=run(config)[1] == 0))
        result = minimise(items, oracle, closure={CHLORIDE: [SODIUM]})
        self.assertEqual({CHLORIDE, CWB}, set(result.minimal))
        self.assertIn(UNRESOLVED, [verdict for _, verdict in result.history])

    def test_non_ok_rows_frames_and_nested_jars_resolve_to_installed_jars(self):
        report = ('{"mods":[{"modId":"hosty","jar":"hosty-2.0.jar","bundledBy":"","status":"OK"},'
                  '{"modId":"libx","jar":"libx-inner.jar","bundledBy":"hosty","status":"FAILED"},'
                  '{"modId":"plain","jar":"plain-1.0.jar","bundledBy":"","status":"DEGRADED"}]}')
        crash = ('java.lang.IllegalStateException: Sources: chloride and ghost\n'
                 '\tat forbric/x.Y.z(Y.java:1) ~[libx-inner.jar:?] {}\n'
                 '\tat forbric/x.Y.w(Y.java:2) ~[frame-only-3.1.jar:?] {}\n')
        jars = ['hosty-2.0.jar', 'plain-1.0.jar', 'frame-only-3.1.jar', 'ghost-1.0.jar', CHLORIDE]
        # ghost has a jar named like it but no report row, so it is not guessed from the file name.
        self.assertEqual(['hosty-2.0.jar', 'frame-only-3.1.jar', 'plain-1.0.jar'], seeds(crash, '', report, jars))


if __name__ == '__main__':
    unittest.main()
