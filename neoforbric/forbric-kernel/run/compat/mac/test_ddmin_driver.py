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
