import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import tempfile
import time
import types
import unittest
from unittest import mock
import zipfile
import ddmin
from ddmin_core import FAIL, PASS, UNRESOLVED
import mixed

HERE = Path(__file__).resolve().parent
SWEEP100 = HERE.parent / 'reports' / '2026-10-01-sweep100'
CHLORIDE = 'chloride-NEOFORGE-mc26.2-v1.8.1.jar'
CWB = 'cwb-4.1.0+26.2.jar'
SODIUM_NEO = 'sodium-neoforge-0.9.2+mc26.2.jar'
SODIUM_FABRIC = 'sodium-fabric-0.9.2+mc26.2.jar'
SSPB = 'sodium-shadowy-path-blocks-fabric-7.0.0.jar'
CLASH = "java.lang.IllegalArgumentException: Multiple overrides for option 'sodium:general.fullscreen_mode'! Sources: chloride and cwb"
ECOSYSTEM = {'neoforge': 'NEOFORGE', 'fabric': 'FABRIC', 'forge': 'FORGE'}
# The order the fake arbitrates in when two jars claim one mod id; the real full pack picked sodium = neoforge.
PREFERENCE = ['NEOFORGE', 'FORGE', 'FABRIC']


def clash(loaded, flags):
    """Sodium's config API refusing chloride and cwb, which it can only do with the NeoForge Sodium running."""
    if {'chloride', 'cwb'} <= loaded.keys() and loaded.get('sodium', {}).get('ecosystem') == 'NEOFORGE':
        return CLASH
    return None


class FakeGame:
    """Minecraft for these tests: reads the mods in the instance and the session's flags, and leaves the files a
    session leaves. failure(loaded, flags) names the exception a configuration dies of, or None for a clean session;
    loaded maps each mod id to the report row of the copy arbitration chose."""

    def __init__(self, root, rows, failure):
        self.instance = root / 'inst'
        self.rows = {row['filename']: row for row in rows}
        self.failure = failure
        self.sessions = []

    def prepare(self, jars):
        clear(self.instance)
        (self.instance / 'mods').mkdir(parents=True)
        for jar in jars:
            (self.instance / 'mods' / jar).write_bytes(b'')
        write(self.instance / 'options.txt', 'pauseOnLostFocus:true\n')

    def driver(self, instance, log, ticks, jvm, stall, timeout, grace):
        jars = sorted(path.name for path in (instance / 'mods').iterdir())
        flags = {}
        for flag in jvm:
            if flag.startswith('-D') and '=' in flag:
                name, value = flag[2:].split('=', 1)
                flags[name] = set(filter(None, value.split(',')))
        self.sessions.append((jars, list(jvm)))
        claims = {}
        for jar in jars:
            row = self.rows[jar]
            mod_id = row.get('mod_id') or row['slug']
            claims.setdefault(mod_id, []).append(dict(modId=mod_id, version=row.get('version', '1.0'),
                                                      ecosystem=ECOSYSTEM[row['loader']], jar=jar, bundledBy='',
                                                      status='OK'))
        loaded = {mod_id: min(copies, key=lambda copy: (PREFERENCE.index(copy['ecosystem']), copy['jar']))
                  for mod_id, copies in claims.items()}
        contested = {mod_id: copy['ecosystem'].lower() for mod_id, copy in loaded.items() if len(claims[mod_id]) > 1}
        if contested:
            choices = ''.join(f'# {mod_id} = {loader}\n' for mod_id, loader in contested.items())
            write(instance / 'forbric-mods.txt', '# Some mods in this instance are installed twice.\n'
                  '#   values: fabric / neoforge / minecraftforge\n\n' + choices)
            write(instance / '.forbric-kernel' / 'merge-report.txt', 'Forbric merge report\n')
        write(instance / '.forbric-kernel' / 'compatibility-report.json',
              json.dumps(dict(confirmedRequired=0, findings=[], catalogFailures=[], mods=list(loaded.values()))))
        exception = self.failure(loaded, flags)
        console = '[Render thread/INFO]: loading\n'
        if exception:
            crash = (f'---- Minecraft Crash Report ----\n// fake\n\nTime: 2026-10-01 21:00:12\nDescription: fake\n\n'
                     f'{exception}\n'
                     '\tat forbric/net.caffeinemc.mods.sodium.client.config.structure.Config.applyOptionChanges'
                     '(Config.java:131) ~[net.caffeinemc.sodium-neoforge-0.9.2+mc26.2-mod.jar:?] {}\n\n\n'
                     'A detailed walkthrough of the error\n---------------------\n')
            write(instance / 'crash-reports' / 'crash-2026-10-01_21.00.12-client.txt', crash)
            sources = exception.split('Sources: ', 1)[1].split(' and ') if 'Sources: ' in exception else []
            write(instance / '.forbric-kernel' / 'crash-analysis.txt', 'Forbric crash analysis\n\n' +
                  ''.join(f'  {mod_id.title()} 1.0  ({mod_id})\n    the error names it as clashing with another mod\n\n'
                          for mod_id in sources))
            write(instance / 'client-console.log', console + 'Game crashed! Crash report saved to: crash-reports\n')
            write(log, 'FAIL client exit=255 joined=False drew=False\n')
            return 1
        world = instance / 'saves' / 'compat-world'
        write(world / 'level.dat', 'level')
        write(world / 'region' / 'r.0.0.mca', 'region')
        write(instance / 'client-console.log', console + '[Render thread/INFO]: joined world via quick-play\n')
        write(log, 'PASS client exit=0 joined=True drew=True\n')
        return 0


def clear(directory):
    """Remove a session's instance, or fail. ignore_errors hid a delete Windows refused while a scanner still held a
    file the last session wrote: its crash report survived, the next clean session read as a crash, and the
    minimiser kept jars it should have dropped (a flake seen only on windows-latest)."""
    for attempt in range(50):
        try:
            shutil.rmtree(directory)
        except FileNotFoundError:
            return
        except OSError:
            if attempt == 49:
                raise
            time.sleep(0.1)
        if not directory.exists():
            return
    raise AssertionError(f'{directory} could not be cleared')


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8')


def zipped(entries):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as archive:
        for name, content in entries.items():
            archive.writestr(name, content if isinstance(content, (bytes, str)) else json.dumps(content))
    return buffer.getvalue()


class Fixture(unittest.TestCase):
    """A temporary sweep data directory: mods/ with one file per manifest row, manifest.json and closure.json."""

    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.data = self.root / 'data'
        (self.data / 'mods').mkdir(parents=True)
        self.enterContext(contextlib.redirect_stdout(io.StringIO()))
        self.kernel = 'k' * 64

    def pack(self, rows, closure, contents=None):
        contents = contents or {}
        for row in rows:
            data = contents.get(row['filename'], row['filename'].encode('utf-8'))
            (self.data / 'mods' / row['filename']).write_bytes(data)
            row.update(sha1=hashlib.sha1(data).hexdigest(), size=len(data))
        (self.data / 'manifest.json').write_text(json.dumps(rows), encoding='utf-8')
        (self.data / 'closure.json').write_text(json.dumps(closure), encoding='utf-8')
        self.rows = rows
        return ddmin.load_pack(self.data / 'manifest.json', self.data)

    def minimise(self, pack, failure, out='out', fingerprint=None, **options):
        self.game = FakeGame(self.root, self.rows, failure)
        runs = self.root / out / 'runs'
        subjects = set(pack.subjects)

        def launch(jars, jvm, label):
            self.game.prepare(jars)
            result = mixed.run(label, options.get('ticks', 200), [jar for jar in jars if jar in subjects], runs,
                               jvm=jvm, instance=self.game.instance, launch=self.game.driver)
            return runs / label, result
        return ddmin.minimise_pack(pack, self.root / out, launch, fingerprint or (lambda: self.kernel),
                                   self.data / 'mods', **options)


def row(filename, loader, kind='random', slug=None, mod_id=None):
    return dict(filename=filename, loader=loader, kind=kind, slug=slug or filename.split('-')[0], mod_id=mod_id)


class KnownAnswerTest(Fixture):
    """The 2026-10-01 sweep100 mixed pack (88 subjects, 109 jars) against a game whose only failure is the clash."""

    def sweep100(self):
        rows = json.loads((SWEEP100 / 'mixed-manifest.json').read_text(encoding='utf-8'))
        for entry in rows:
            entry['mod_id'] = {CHLORIDE: 'chloride', CWB: 'cwb'}.get(entry['filename'], entry['slug'])
        return self.pack(rows, json.loads((SWEEP100 / 'closure.json').read_text(encoding='utf-8')))

    def test_the_pack_reduces_to_chloride_and_cwb_plus_sodium(self):
        pack = self.sweep100()
        self.assertEqual((88, 109), (len(pack.subjects), len(pack.jars)))
        result = self.minimise(pack, clash)
        first = result['rounds'][0]
        self.assertEqual('MINIMISED', result['status'])
        self.assertEqual([CHLORIDE, CWB], sorted(first['minimal']))
        self.assertEqual([CHLORIDE, CWB, SODIUM_NEO], sorted(first['closed']))
        self.assertTrue(first['seeded'])
        self.assertEqual(CLASH, first['reference']['signature'])
        self.assertEqual(109, first['reference']['jars'])
        # The reference, the seed set, then each of the pair alone.
        self.assertEqual(4, result['launches'])
        self.assertEqual('neoforge', first['reference']['arbitration']['sodium']['loader'])
        version = next(entry['version'] for entry in self.rows if entry['filename'] == SODIUM_NEO)
        self.assertEqual(['NEOFORGE', SODIUM_NEO, version], first['reference']['arbitration']['sodium']['copy'])
        written = json.loads((self.root / 'out' / 'ddmin-result.json').read_text(encoding='utf-8'))
        self.assertEqual(sorted(first['minimal']), sorted(written['rounds'][0]['minimal']))

    def test_without_seeds_the_search_still_reaches_the_pair(self):
        result = self.minimise(self.sweep100(), clash, use_seeds=False)
        first = result['rounds'][0]
        self.assertEqual([CHLORIDE, CWB], sorted(first['minimal']))
        self.assertFalse(first['seeded'])
        # 30 measured; one session on the way is UNRESOLVED because it brought only the Fabric Sodium.
        self.assertLessEqual(result['launches'], 40)

    def test_a_budget_stops_the_search_and_keeps_the_smallest_failure(self):
        result = self.minimise(self.sweep100(), clash, budget=2)
        first = result['rounds'][0]
        self.assertEqual('BUDGET', result['status'])
        self.assertEqual(2, result['launches'])
        self.assertEqual([CHLORIDE, CWB], sorted(first['smallest_failing']))
        self.assertEqual([CHLORIDE, CWB, SODIUM_NEO], sorted(first['smallest_failing_closed']))


class ArbitrationTest(Fixture):
    def small(self):
        rows = [row('filler1-1.0.jar', 'fabric'), row(SSPB, 'fabric', slug='sspb'), row(CWB, 'forge', 'popular', 'cwb'),
                row('filler2-1.0.jar', 'fabric'), row(CHLORIDE, 'neoforge', 'popular', 'chloride'),
                row('filler3-1.0.jar', 'fabric'), row(SODIUM_NEO, 'neoforge', 'dep', 'sodium'),
                row(SODIUM_FABRIC, 'fabric', 'dep', 'sodium')]
        closure = {name['filename']: [] for name in rows}
        closure.update({SSPB: [SODIUM_FABRIC], CHLORIDE: [SODIUM_NEO]})
        return self.pack(rows, closure)

    def test_a_subset_that_loads_the_other_sodium_is_unresolved(self):
        # This failure needs cwb and any Sodium. {cwb, sspb} brings only the Fabric build, which the full pack did not
        # run: taking that crash for the failure would answer with a pack nobody installed.
        def any_sodium(loaded, flags):
            return CLASH if 'cwb' in loaded and 'sodium' in loaded else None
        result = self.minimise(self.small(), any_sodium, use_seeds=False)
        first = result['rounds'][0]
        self.assertEqual([CHLORIDE, CWB], sorted(first['minimal']))
        self.assertNotIn(SODIUM_FABRIC, first['closed'])
        differing = [run for run in first['runs'] if run.get('winner_differences')]
        self.assertTrue(differing)
        self.assertTrue(all(run['verdict'] == UNRESOLVED for run in differing))
        self.assertIn(f'sodium: NEOFORGE/{SODIUM_NEO}/1.0 -> FABRIC/{SODIUM_FABRIC}/1.0',
                      differing[0]['winner_differences'])

    def test_differences_are_read_from_rows_and_from_forbric_mods(self):
        neoforge = json.dumps({'mods': [dict(modId='sodium', ecosystem='NEOFORGE', jar=SODIUM_NEO, version='1')]})
        reference = ddmin.winners(neoforge, '# sodium = neoforge\n')
        same = ddmin.winners(neoforge, None)
        self.assertEqual([], ddmin.winner_differences(reference, same))
        self.assertEqual(['no compatibility-report.json to compare the loaded copies with'],
                         ddmin.winner_differences(reference, ddmin.winners('', '# sodium = neoforge\n')))
        extra = ddmin.winners(json.dumps({'mods': [dict(modId='other', ecosystem='FABRIC', jar='o.jar', version='2')]}), None)
        self.assertEqual(['other: FABRIC/o.jar/2, which the reference did not load'],
                         ddmin.winner_differences(reference, extra))
        # Without a report on either side, forbric-mods.txt is all there is.
        self.assertEqual(['sodium = neoforge -> fabric'],
                         ddmin.winner_differences(ddmin.winners('', '# sodium = neoforge\n'),
                                                  ddmin.winners('', 'sodium = fabric # pinned\n')))
        self.assertEqual([], ddmin.winner_differences(ddmin.winners('', None), ddmin.winners('', None)))

    def test_forbric_mods_headers_in_either_language_are_not_choices(self):
        text = ('# Every such mod is listed below with the copy the kernel chose.\n'
                '#   values: fabric / neoforge / minecraftforge\n'
                '#   可填:fabric / neoforge / minecraftforge\n'
                '# .forbric-kernel/merge-report.txt\n\n'
                '# fabric-api-base = fabric\n# sodium = neoforge\nspectrelib = NeoForge\n')
        self.assertEqual({'fabric-api-base': 'fabric', 'sodium': 'neoforge', 'spectrelib': 'neoforge'},
                         ddmin.winners('', text)['picks'])


class CacheTest(Fixture):
    def simple(self):
        rows = [row(f'mod{i}-1.0.jar', 'fabric') for i in range(6)] + [row(CWB, 'forge', 'popular', 'cwb'),
                                                                       row(CHLORIDE, 'neoforge', 'popular', 'chloride'),
                                                                       row(SODIUM_NEO, 'neoforge', 'dep', 'sodium')]
        closure = {entry['filename']: [] for entry in rows}
        closure[CHLORIDE] = [SODIUM_NEO]
        return self.pack(rows, closure)

    def test_a_second_invocation_launches_nothing(self):
        pack = self.simple()
        first = self.minimise(pack, clash)
        again = self.minimise(pack, clash)
        self.assertGreater(first['launches'], 0)
        self.assertEqual(0, again['launches'])
        self.assertEqual(first['rounds'][0]['minimal'], again['rounds'][0]['minimal'])
        self.assertEqual([], self.game.sessions)
        lines = (self.root / 'out' / 'cache.jsonl').read_text(encoding='utf-8').splitlines()
        self.assertEqual(first['launches'], len(lines))
        entry = json.loads(lines[0])
        self.assertEqual(self.kernel, entry['kernel_sha256'])
        self.assertEqual(sorted(entry['jars']), entry['jars'])
        self.assertEqual(pack.digests[CWB], entry['jar_sha256'][CWB])
        self.assertTrue((self.root / 'out' / 'runs' / entry['label'] / 'result.json').is_file())

    def test_a_cache_from_another_kernel_is_refused(self):
        pack = self.simple()
        self.minimise(pack, clash)
        with self.assertRaisesRegex(SystemExit, 'holds sessions of kernel k+, but the installed kernel is other'):
            self.minimise(pack, clash, fingerprint=lambda: 'other')

    def test_a_kernel_that_changes_while_it_runs_is_refused(self):
        # Read at the start, then before and after every launch.
        between = iter(['k1', 'k1', 'k1', 'k2'])
        with self.assertRaisesRegex(SystemExit, 'kernel changed since this minimisation started'):
            self.minimise(self.simple(), clash, fingerprint=lambda: next(between))
        during = iter(['k1', 'k1', 'k1', 'k1', 'k2'])
        with self.assertRaisesRegex(SystemExit, 'kernel changed during session 002-'):
            self.minimise(self.simple(), clash, out='during', fingerprint=lambda: next(during))

    def test_the_key_is_the_kernel_the_jar_bytes_the_flags_and_the_ticks(self):
        pack = self.simple()

        def key(jars, jvm=(), kernel=self.kernel, digests=pack.digests, ticks=200):
            sessions = ddmin.Sessions(self.root / 'keys', None, lambda: kernel, digests, ticks, 10, self.data / 'mods')
            return sessions.key(jars, jvm)
        base = key([CWB, CHLORIDE])
        self.assertEqual(base, key([CHLORIDE, CWB]))
        self.assertNotEqual(base, key([CHLORIDE, CWB], ['-Dforbric.suppressMixins=a:B']))
        self.assertNotEqual(base, key([CWB, CHLORIDE], ticks=6000))
        self.assertNotEqual(base, key([CWB, CHLORIDE], kernel='k2'))
        self.assertNotEqual(base, key([CWB, CHLORIDE], digests=dict(pack.digests, **{CWB: '0' * 64})))

    def test_a_jar_that_changes_during_the_minimisation_is_refused(self):
        pack = self.simple()

        def failure(loaded, flags):
            (self.data / 'mods' / CWB).write_bytes(b'rebuilt')
            return clash(loaded, flags)
        with self.assertRaisesRegex(SystemExit, 'Test input changed during session 001-[0-9a-f]+: ' + CWB.replace('+', r'\+')):
            self.minimise(pack, failure)
        self.assertFalse((self.root / 'out' / 'cache.jsonl').exists())

    def test_a_jar_outside_the_later_sessions_is_checked_before_the_result(self):
        pack = self.simple()
        calls = []

        def failure(loaded, flags):
            calls.append(set(loaded))
            if len(calls) == 2:
                # After the reference, and in no later configuration: only the final check sees it.
                (self.data / 'mods' / 'mod0-1.0.jar').write_bytes(b'rebuilt')
            return clash(loaded, flags)
        with self.assertRaisesRegex(SystemExit, 'Test input changed during the minimisation: mod0-1.0.jar'):
            self.minimise(pack, failure)
        self.assertFalse((self.root / 'out' / 'ddmin-result.json').exists())

    def test_a_passing_pack_has_nothing_to_minimise(self):
        result = self.minimise(self.simple(), lambda loaded, flags: None)
        self.assertEqual('PASSED', result['status'])
        self.assertEqual(1, result['launches'])


class IterateTest(Fixture):
    def test_each_round_takes_the_previous_culprits_out(self):
        rows = [row(f'mod{i}-1.0.jar', 'fabric') for i in range(10)]
        a1, a2, b = 'mod1-1.0.jar', 'mod6-1.0.jar', 'mod3-1.0.jar'
        pack = self.pack(rows, {entry['filename']: [] for entry in rows})

        def failure(loaded, flags):
            if {'mod1', 'mod6'} <= loaded.keys():
                return 'java.lang.IllegalStateException: first failure'
            if 'mod3' in loaded:
                return 'java.lang.NullPointerException: second failure'
            return None
        result = self.minimise(pack, failure, iterate=True)
        self.assertEqual(['MINIMISED', 'MINIMISED', 'PASSED'], [record['status'] for record in result['rounds']])
        self.assertEqual([a1, a2], sorted(result['rounds'][0]['minimal']))
        self.assertEqual([b], result['rounds'][1]['minimal'])
        self.assertEqual('java.lang.NullPointerException: second failure', result['rounds'][1]['reference']['signature'])
        self.assertEqual(8, result['rounds'][1]['candidates'])
        self.assertEqual('PASSED', result['status'])
        without = self.minimise(pack, failure, out='single')
        self.assertEqual(['MINIMISED'], [record['status'] for record in without['rounds']])


class NarrowTest(Fixture):
    def jars(self):
        server_only = {'config': 'alpha.server.mixins.json', 'environment': 'server'}
        alpha = zipped({'fabric.mod.json': {'id': 'alpha', 'mixins': ['alpha.mixins.json', server_only]},
                        'alpha.mixins.json': {'package': 'a', 'mixins': ['One', 'Two'], 'client': ['Three'],
                                              'server': ['Four']},
                        'alpha.server.mixins.json': {'package': 'a', 'mixins': ['Five']}})
        toml = 'modLoader = "javafml"\n[[mods]]\nmodId = "beta"\n\n[[mixins]]\nconfig = "beta.mixins.json"\n'
        beta = zipped({'META-INF/neoforge.mods.toml': toml,
                       'beta.mixins.json': {'package': 'b', 'mixins': ['BOne']}})
        inner = zipped({'gamma.mixins.json': {'package': 'g', 'mixins': ['GOne'], 'client': ['GTwo']}})
        gamma = zipped({'META-INF/MANIFEST.MF': 'Manifest-Version: 1.0\nMixinConfigs: gamma.mixins.json\n',
                        'META-INF/jarjar/metadata.json': {'jars': [{'path': 'META-INF/jarjar/gamma-mod.jar'}]},
                        'META-INF/jarjar/gamma-mod.jar': inner})
        return {'alpha-1.0.jar': alpha, 'beta-1.0.jar': beta, 'gamma-1.0.jar': gamma}

    def narrow_pack(self):
        contents = self.jars()
        rows = [row('alpha-1.0.jar', 'fabric'), row('filler-1.0.jar', 'fabric'), row('gamma-1.0.jar', 'forge'),
                row('beta-1.0.jar', 'neoforge'), row('other-1.0.jar', 'fabric')]
        contents['filler-1.0.jar'] = zipped({'fabric.mod.json': {'id': 'filler'}})
        contents['other-1.0.jar'] = zipped({'fabric.mod.json': {'id': 'other'}})
        return self.pack(rows, {entry['filename']: [] for entry in rows}, contents)

    def test_configs_are_read_as_each_loader_declares_them(self):
        configs = ddmin.mixin_configs(self.jars().values())
        self.assertEqual({'alpha.mixins.json': ['One', 'Two', 'Three'], 'beta.mixins.json': ['BOne'],
                          'gamma.mixins.json': ['GOne', 'GTwo']}, configs)

    def test_the_failure_narrows_to_one_config_and_one_class(self):
        def failure(loaded, flags):
            disabled = flags.get('forbric.disableMixinConfigs', set())
            suppressed = flags.get('forbric.suppressMixins', set())
            if {'alpha', 'beta'} <= loaded.keys() and 'alpha.mixins.json' not in disabled \
                    and 'alpha.mixins.json:Three' not in suppressed:
                return 'java.lang.IllegalStateException: alpha meets beta'
            return None
        result = self.minimise(self.narrow_pack(), failure, narrowing=True)
        first = result['rounds'][0]
        self.assertEqual(['alpha-1.0.jar', 'beta-1.0.jar'], sorted(first['minimal']))
        self.assertEqual(['alpha.mixins.json'], first['narrow']['configs'])
        self.assertEqual(['alpha.mixins.json:Three'], first['narrow']['classes'])
        last = first['narrow']['class_runs'][-1]['jvm']
        self.assertIn('-Dforbric.disableMixinConfigs=beta.mixins.json', last)

    def test_a_failure_with_every_config_off_needs_none(self):
        def failure(loaded, flags):
            return 'java.lang.IllegalStateException: alpha meets beta' if {'alpha', 'beta'} <= loaded.keys() else None
        first = self.minimise(self.narrow_pack(), failure, narrowing=True)['rounds'][0]
        self.assertEqual([], first['narrow']['configs'])
        self.assertIn('disabled', first['narrow']['note'])
        self.assertEqual(['-Dforbric.disableMixinConfigs=alpha.mixins.json,beta.mixins.json'],
                         first['narrow']['config_runs'][0]['jvm'])

    def test_narrowing_owns_its_two_flags(self):
        with self.assertRaisesRegex(SystemExit, 'leave them out of --jvm'):
            self.minimise(self.narrow_pack(), lambda loaded, flags: None, narrowing=True,
                          jvm=['-Dforbric.suppressMixins=a.mixins.json:A'])


class MainTest(Fixture):
    def test_the_command_drives_mixed_against_the_installed_profile(self):
        rows = [row(f'mod{i}-1.0.jar', 'fabric') for i in range(5)] + [row(CWB, 'forge', 'popular', 'cwb'),
                                                                       row(CHLORIDE, 'neoforge', 'popular', 'chloride'),
                                                                       row(SODIUM_NEO, 'neoforge', 'dep', 'sodium')]
        closure = {entry['filename']: [] for entry in rows}
        closure[CHLORIDE] = [SODIUM_NEO]
        self.pack(rows, closure)
        game = FakeGame(self.root, self.rows, clash)
        self.addCleanup(setattr, mixed, '_permod', None)
        mixed._permod = types.SimpleNamespace(INST=game.instance, prepare=game.prepare, kernel_fingerprint=lambda: self.kernel)
        with mock.patch.dict(os.environ, {'PERMOD_DATA': str(self.data)}), mock.patch.object(mixed, 'drive', game.driver):
            code = ddmin.main(['--manifest', str(self.data / 'manifest.json'), '--jvm=-Dforbric.example=1',
                               '--out', str(self.root / 'evidence')])
        self.assertEqual(0, code)
        result = json.loads((self.root / 'evidence' / 'ddmin' / 'ddmin-result.json').read_text(encoding='utf-8'))
        self.assertEqual([CHLORIDE, CWB], sorted(result['rounds'][0]['minimal']))
        self.assertEqual(mixed.sha256(self.data / 'manifest.json'), result['manifest_sha256'])
        self.assertEqual(mixed.sha256(self.data / 'closure.json'), result['closure_sha256'])
        self.assertTrue(all('-Dforbric.example=1' in jvm for _, jvm in game.sessions))
        self.assertIn('pauseOnLostFocus:false', (game.instance / 'options.txt').read_text(encoding='utf-8'))
        label = result['rounds'][0]['reference']['label']
        self.assertTrue((self.root / 'evidence' / 'ddmin' / 'runs' / label / 'crash-analysis.txt').is_file())


class EvidenceTest(unittest.TestCase):
    def test_the_game_exit_code_comes_from_the_driver_line(self):
        self.assertEqual(78, ddmin.game_exit('client language en_us\nFAIL client exit=78 joined=False drew=False\n'))
        self.assertIsNone(ddmin.game_exit('FAIL client remained alive after grace\n'))

    def test_a_failure_no_exception_names_carries_the_session_outcome(self):
        stall = dict(run='STALL', bad_mods=[], saved=False)
        self.assertEqual('STALL EXIT:None', ddmin.outcome_signature('EXIT:None', stall))
        degraded = dict(run='PASS', saved=True,
                        bad_mods=[{'modId': 'zeta', 'status': 'DEGRADED'}, {'modId': 'alpha', 'status': 'FAILED'}])
        self.assertEqual('PASS EXIT:0 bad:alpha=FAILED,zeta=DEGRADED', ddmin.outcome_signature('EXIT:0', degraded))
        self.assertEqual('PASS EXIT:0 unsaved', ddmin.outcome_signature('EXIT:0', dict(run='PASS', saved=False)))
        self.assertEqual(CLASH, ddmin.outcome_signature(CLASH, stall))

    def test_a_policy_stop_is_judged_by_its_findings(self):
        reference = dict(signature='POLICY_STOP:alpha:initialization:alpha', winners=dict(rows=None, picks=None))
        self.assertEqual((FAIL, []), ddmin.judge(reference, dict(reference, strict=False)))
        self.assertEqual((PASS, []), ddmin.judge(reference, dict(reference, strict=True, signature='PASS EXIT:0')))
        self.assertEqual((UNRESOLVED, []), ddmin.judge(reference, dict(reference, strict=False, signature='POLICY_STOP:')))


class PackTest(Fixture):
    def test_candidates_are_the_subjects_and_any_jar_nobody_needs(self):
        rows = [row('a-1.0.jar', 'fabric', 'popular'), row('lib-1.0.jar', 'fabric', 'dep'),
                row('loose-1.0.jar', 'fabric', 'dep')]
        pack = self.pack(rows, {'a-1.0.jar': ['lib-1.0.jar'], 'lib-1.0.jar': [], 'loose-1.0.jar': []})
        self.assertEqual(['a-1.0.jar'], pack.subjects)
        self.assertEqual(['a-1.0.jar', 'loose-1.0.jar'], pack.candidates)

    def test_a_pack_that_cannot_close_or_whose_jars_changed_is_refused(self):
        rows = [row('a-1.0.jar', 'fabric', 'popular')]
        with self.assertRaisesRegex(SystemExit, 'does not hold: lib-1.0.jar'):
            self.pack(rows, {'a-1.0.jar': ['lib-1.0.jar']})
        with self.assertRaisesRegex(SystemExit, 'no entry for a-1.0.jar'):
            self.pack([row('a-1.0.jar', 'fabric', 'popular')], {})
        self.pack([row('a-1.0.jar', 'fabric', 'popular')], {'a-1.0.jar': []})
        (self.data / 'mods' / 'a-1.0.jar').write_bytes(b'changed')
        with self.assertRaisesRegex(SystemExit, 'a-1.0.jar: sha1 differs'):
            ddmin.load_pack(self.data / 'manifest.json', self.data)

    def test_the_command_needs_a_manifest(self):
        with self.assertRaises(SystemExit) as refused, contextlib.redirect_stderr(io.StringIO()):
            ddmin.main([])
        self.assertEqual(2, refused.exception.code)


if __name__ == '__main__':
    unittest.main()
