package net.forbric.kernel.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WindowsDriversTest {
    @TempDir Path temp;

    @Test void launcherPrintsParameterizedConfiguration() throws Exception { checkConfig("forbric-launch.py"); }
    @Test void serverLauncherPrintsParameterizedConfiguration() throws Exception { checkConfig("forbric-server.py"); }
    @Test void serverDriverPrintsParameterizedConfiguration() throws Exception { checkConfig("run-server-test.py"); }
    @Test void clientDriverPrintsParameterizedConfiguration() throws Exception { checkConfig("run-client-test.py"); }
    @Test void bisectPrintsParameterizedConfiguration() throws Exception { checkConfig("bisect.py"); }
    @Test void sharedHelpersPrintParameterizedConfiguration() throws Exception { checkConfig("common.py"); }

    private void checkConfig(String script) throws Exception {
        Map<String, String> environment = Map.of("FORBRIC_MC", temp.resolve("installation").toString(),
                "FORBRIC_VERSION", "test-version", "FORBRIC_INSTANCE", temp.resolve("instance").toString(),
                "FORBRIC_WORLD", "test-world");
        var result = DriverTools.script("win/" + script, environment, "--print-config");
        assertEquals(0, result.exit(), result.output());
        assertTrue(result.output().contains("\"version\": \"test-version\""), result.output());
        assertTrue(result.output().contains("\"world\": \"test-world\""), result.output());
        assertTrue(result.output().contains(".forbric-sweep.pid"), result.output());
        assertTrue(result.output().contains("clientSmokeScreenshots=100"), result.output());
        assertTrue(result.output().contains(temp.resolve("instance").toString()), result.output());
        assertFalse(Files.exists(temp.resolve("instance")), "--print-config must not mutate the installation");
        result = DriverTools.script("win/" + script, environment, "--print-config", "--version", "override-version", "--world", "override-world");
        assertEquals(0, result.exit(), result.output());
        assertTrue(result.output().contains("\"version\": \"override-version\""), result.output());
        assertTrue(result.output().contains("\"world\": \"override-world\""), result.output());
    }

    @Test void compilesEveryWindowsDriverWithoutImportingWindowsApis() throws Exception {
        for (String filename : List.of("common.py", "forbric-launch.py", "forbric-server.py", "run-server-test.py", "run-client-test.py", "bisect.py")) {
            var result = DriverTools.run(Map.of("PYTHONPYCACHEPREFIX", temp.resolve("pycache").toString()),
                    "-m", "py_compile", DriverTools.COMPAT.resolve("win").resolve(filename).toString());
            assertEquals(0, result.exit(), result.output());
        }
    }

    /**
     * A boot that has stopped talking must be told apart from one that is merely slow.
     *
     * Before await_outcome existed there was only --boot-timeout, and a wedged server held it for the whole
     * 900 seconds: two runs in build/compat/ cost 820s and 1615s to report a failure their console logs had
     * already settled inside the first 20 seconds. The numbers below are the ones that evidence supports —
     * across sixteen recorded sweeps a boot that reached Done never went quiet for more than 8 seconds, and
     * every boot that did not went silent 13-17 seconds in and stayed that way.
     *
     * The negative control is the half that matters. A stall detector that fires on a slow machine does not
     * save fifteen minutes, it invents a red sweep, so this asserts that output arriving steadily keeps the
     * wait alive well past the stall window.
     */
    @Test void aBootThatStopsTalkingIsCutShortAndOneThatKeepsTalkingIsNot() throws Exception {
        var result = DriverTools.run(Map.of(), "-c", """
                import sys, types
                sys.path.insert(0, sys.argv[1]); import common

                class Event:
                    def __init__(self): self.value = False
                    def is_set(self): return self.value
                class Process:
                    def __init__(self, exit_at=None): self.exit_at = exit_at
                    def poll(self): return 0 if self.exit_at is not None and clock[0] >= self.exit_at else None

                clock = [0.0]
                def now(): return clock[0]
                def tick(seconds): clock[0] += seconds

                # 1. silent from the start: stalls at the threshold, NOT at the ceiling.
                last = [0.0]
                verdict = common.await_outcome(ready=Event(), failed=Event(), process=Process(),
                                               timeout=900, stall=120, last_output=last, now=now, sleep=tick)
                assert verdict == 'stalled', verdict
                assert 120 <= clock[0] <= 122, clock[0]

                # 2. NEGATIVE CONTROL: still printing, just slowly. Must never stall, however long it takes.
                clock[0] = 0.0; last = [0.0]; ready = Event()
                def talk(seconds):
                    tick(seconds)
                    last[0] = clock[0]          # a line arrived on every poll
                    if clock[0] >= 600: ready.value = True
                verdict = common.await_outcome(ready=ready, failed=Event(), process=Process(),
                                               timeout=900, stall=120, last_output=last, now=now, sleep=talk)
                assert verdict == 'ready', verdict
                assert clock[0] >= 600, clock[0]

                # 3. a boot quiet for 119s and then noisy again is not a stall either.
                clock[0] = 0.0; last = [0.0]; ready = Event()
                def late(seconds):
                    tick(seconds)
                    if clock[0] >= 119: last[0] = clock[0]
                    if clock[0] >= 200: ready.value = True
                assert common.await_outcome(ready=ready, failed=Event(), process=Process(), timeout=900,
                                            stall=120, last_output=last, now=now, sleep=late) == 'ready'

                # 4. the other verdicts keep the precedence the drivers' own conditions had.
                clock[0] = 0.0; last = [0.0]
                both = Event(); both.value = True; failed = Event(); failed.value = True
                assert common.await_outcome(ready=both, failed=failed, process=Process(), timeout=900,
                                            stall=120, last_output=last, now=now, sleep=tick) == 'failed'
                clock[0] = 0.0; last = [0.0]; ready = Event(); ready.value = True
                assert common.await_outcome(ready=ready, failed=Event(), process=Process(exit_at=0), timeout=900,
                                            stall=120, last_output=last, now=now, sleep=tick) == 'exited'
                clock[0] = 0.0; last = [0.0]
                def quiet_but_fed(seconds):
                    tick(seconds); last[0] = clock[0]
                assert common.await_outcome(ready=Event(), failed=Event(), process=Process(), timeout=300,
                                            stall=120, last_output=last, now=now, sleep=quiet_but_fed) == 'timeout'
                print('stall detection PASS')
                """, DriverTools.COMPAT.resolve("win").toString());
        assertEquals(0, result.exit(), result.output());
        assertTrue(result.output().contains("stall detection PASS"), result.output());
    }

    /**
     * The soak has to be able to tick for the whole of --tick-seconds, which means the empty-server pause has
     * to be off.
     *
     * Vanilla defaults pause-when-empty-seconds to 60 and a sweep's server never has a player on it, so
     * MinecraftServer.tickServer stops ticking at Done+60 and returns before tickCount++ and before
     * fireServerTickPre. The sweeps in build/compat/ ran a 90-second soak against that: `Server empty for 60
     * seconds, pausing` lands at Done+60 and nothing follows it until the stop at Done+90. A third of every
     * soak proved nothing, and nobody could see it, because the symptom is silence.
     *
     * So this pins the property rather than the duration. --tick-seconds is a knob someone may reasonably
     * raise; if this line ever goes missing again, every second above sixty is dead and the run still says
     * PASS. Zero disables the pause — it does not mean pause immediately, which is the reading that would
     * gut the soak entirely.
     */
    @Test void theSweepServerNeverPausesItselfForBeingEmpty() throws Exception {
        var result = DriverTools.run(Map.of(), "-c", """
                import sys
                sys.path.insert(0, sys.argv[1]); import common
                written = common.server_properties('compat-world', '20260919', 25599)
                settings = dict(line.split('=', 1) for line in written.splitlines() if line)
                assert settings['pause-when-empty-seconds'] == '0', written
                assert settings['level-name'] == 'compat-world', written
                assert settings['level-seed'] == '20260919', written
                assert settings['server-port'] == '25599', written
                assert settings['online-mode'] == 'false', written
                assert settings['simulation-distance'] == '10', written
                print('server properties PASS')
                """, DriverTools.COMPAT.resolve("win").toString());
        assertEquals(0, result.exit(), result.output());
        assertTrue(result.output().contains("server properties PASS"), result.output());
    }

    @Test void aFirstRunScreenIsMarkedSeenWithoutTouchingTheModsOtherSettings() throws Exception {
        // sweep90-win-r6 sat on wover-ui's BetterX welcome for five minutes: vanilla runs quick-play only after it.
        var result = DriverTools.run(Map.of(), "-c", """
                import json, pathlib, sys
                sys.path.insert(0, sys.argv[1]); import common
                instance = pathlib.Path(sys.argv[2])
                common.acknowledge_first_run(instance)
                fresh = json.loads((instance / 'config/wover/client.json').read_text())
                assert fresh == {'internal': {'did_present_welcome_screen': True}}, fresh
                (instance / 'config/wover/client.json').write_text(json.dumps({'create_version': '26.201.2',
                    'internal': {'did_present_welcome_screen': False}, 'general': {'check_for_new_versions': True}}))
                common.acknowledge_first_run(instance)
                merged = json.loads((instance / 'config/wover/client.json').read_text())
                assert merged == {'create_version': '26.201.2', 'internal': {'did_present_welcome_screen': True},
                                  'general': {'check_for_new_versions': True}}, merged
                (instance / 'config/wover/client.json').write_text('not json')
                common.acknowledge_first_run(instance)
                assert json.loads((instance / 'config/wover/client.json').read_text())['internal']['did_present_welcome_screen']
                for driver in ('run-client-test.py', 'bisect.py'):
                    source = (pathlib.Path(sys.argv[1]) / driver).read_text()
                    assert source.index('acknowledge_first_run(instance)') < source.index("driver_command(configuration, 'forbric-launch.py')"), driver
                print('first run PASS')
                """, DriverTools.COMPAT.resolve("win").toString(), temp.toString());
        assertEquals(0, result.exit(), result.output());
        assertTrue(result.output().contains("first run PASS"), result.output());
    }

    /**
     * A sweep's driver as far as options.txt goes, for the tests below that kill one: it pins, its client rewrites the
     * file while it loads (startedCleanly:false, and a key of a mod the pack does not have drops out), and it waits.
     * Closing its stdin ends the block normally; kill() is taskkill /F, and the block's finally never runs.
     */
    private static final String SWEEP_DRIVER = """
            LOADING = '\\n'.join([
                'import pathlib, sys',
                'sys.path.insert(0, sys.argv[1]); import common',
                'options = pathlib.Path(sys.argv[2]) / "options.txt"',
                'with common.sweep_language(sys.argv[2], sys.argv[3]):',
                '    text = options.read_bytes() if options.exists() else b"version:4671\\\\nlang:en_us\\\\n"',
                '    options.write_bytes(text.replace(b"startedCleanly:true", b"startedCleanly:false").replace(b"key_key.xaero_minimap:key.keyboard.y\\\\r\\\\n", b"")',
                '                        + (b"" if b"startedCleanly" in text else b"startedCleanly:false\\\\n"))',
                '    print("loading", flush=True)',
                '    sys.stdin.read()',
            ])
            def loading(code='en_us'):
                child = subprocess.Popen([sys.executable, '-c', LOADING, sys.argv[1], str(instance), code],
                                         stdin=subprocess.PIPE, stdout=subprocess.PIPE)
                assert child.stdout.readline() == b'loading\\n'
                return child
            """;

    /**
     * A sweep plays en_us whoever runs it, and the player's options.txt comes back byte for byte — over the client's
     * own rewrite of it too, in every --lang, and without a file when the player had none.
     *
     * sweep90-win-r7c inherited its player's zh_cn and died at world join: Axiom 6.1.3's bundled Dear ImGui 1.92.7
     * keeps a pointer into the font byte[]s past the JNI call that pinned them, and the two CJK fonts it loads for
     * zh (19 MB) are humongous reads that start the GC which moves or frees them before the atlas builds —
     * imstb_truetype.h:1590, then System.exit(1). Native Fabric with only Axiom and fabric-api asserts the same way
     * once a GC lands between the add and the build (forced, or under -XX:+UseSerialGC -Xmn16m; under default G1 that
     * minimal pack did not crash in the runs recorded), so the kernel stays as it is; what was wrong is a verdict that
     * depended on who ran the sweep. The file below is Windows-shaped (CRLF, the player's other settings around the
     * line) because that is the file a sweep edits.
     */
    @Test void theClientPlaysOneLanguageAndThePlayersOptionsComeBackByteForByte() throws Exception {
        var result = DriverTools.run(Map.of(), "-c", """
                import base64, json, os, pathlib, sys
                sys.path.insert(0, sys.argv[1]); import common
                instance = pathlib.Path(sys.argv[2]); instance.mkdir()
                options, record = instance / 'options.txt', instance / 'options.txt.forbric-sweep'
                player = b'version:4671\\r\\nlang:zh_cn\\r\\nonboardAccessibility:false\\r\\nstartedCleanly:true\\r\\nguiScale:2\\r\\n'
                def rewrite():  # what the client saves while it loads: Minecraft.<init> sets startedCleanly false and saves
                    options.write_bytes(options.read_bytes().replace(b'startedCleanly:true', b'startedCleanly:false') + b'tutorialStep:none\\r\\n')
                options.write_bytes(player)
                with common.sweep_language(instance) as played:
                    assert played == "en_us; the player's options.txt names zh_cn, restored after the run", played
                    assert options.read_bytes() == player.replace(b'lang:zh_cn', b'lang:en_us'), options.read_bytes()
                    kept = json.loads(record.read_text())
                    assert (kept['lang'], kept['was'], kept['absent'], kept['pid']) == ('en_us', 'zh_cn', False, os.getpid()), kept
                    assert base64.b64decode(kept['original']) == player and kept['started'] == common.process_started(os.getpid()), kept
                    rewrite()
                assert options.read_bytes() == player and not record.exists(), options.read_bytes()
                try:
                    with common.sweep_language(instance):
                        rewrite()
                        raise RuntimeError('client died')
                except RuntimeError:
                    pass
                assert options.read_bytes() == player and not record.exists()
                # `player` pins nothing, and the client's rewrite still goes.
                with common.sweep_language(instance, 'player') as played:
                    assert played == "zh_cn; the player's options.txt, played as it is and restored after the run", played
                    assert options.read_bytes() == player and json.loads(record.read_text())['lang'] is None
                    rewrite()
                assert options.read_bytes() == player and not record.exists()
                # No lang line: one is appended for the run and the file comes back without it.
                bare = b'version:4671\\nguiScale:2'
                options.write_bytes(bare)
                with common.sweep_language(instance) as played:
                    assert 'names no language (vanilla plays en_us)' in played and options.read_bytes() == bare + b'\\nlang:en_us\\n'
                    assert json.loads(record.read_text())['was'] is None
                assert options.read_bytes() == bare
                # No options.txt: none is written for the run, and the one the client writes goes afterwards.
                options.unlink()
                for code in ('en_us', 'player'):
                    with common.sweep_language(instance, code) as played:
                        assert played.startswith('en_us; the player has no options.txt (vanilla plays en_us)'), played
                        kept = json.loads(record.read_text())
                        assert not options.exists() and kept['absent'] is True and kept['lang'] is None and kept['original'] is None, kept
                        options.write_bytes(b'version:4671\\nlang:en_us\\nstartedCleanly:false\\n')
                    assert not options.exists() and not record.exists(), code
                for bad in ('zh_cn\\nguiScale:4', ''):
                    try:
                        with common.sweep_language(instance, bad):
                            raise AssertionError('entered with ' + repr(bad))
                    except ValueError:
                        pass
                try:
                    with common.sweep_language(instance, 'zh_cn'):
                        raise AssertionError('no options.txt, yet zh_cn was promised')
                except ValueError:
                    pass
                assert not options.exists() and not record.exists()
                for driver in ('run-client-test.py', 'bisect.py'):
                    text = (pathlib.Path(sys.argv[1]) / driver).read_text()
                    assert 'language_argument(argument_parser)' in text, driver
                    lines = text.splitlines()
                    pin = next(i for i, text in enumerate(lines) if 'with sweep_language(instance, args.lang) as played' in text)
                    said = next(i for i, text in enumerate(lines) if "print('client language ' + played, flush=True)" in text)
                    run = next(i for i, text in enumerate(lines) if 'spawn(configuration, command' in text)
                    stop = next(i for i, text in enumerate(lines) if 'finish(configuration, process)' in text)
                    depth = len(lines[pin]) - len(lines[pin].lstrip())
                    assert pin < said < run < stop, driver
                    assert all(len(text) - len(text.lstrip()) > depth for text in lines[pin + 2:stop + 1] if text.strip()), driver
                print('language PASS')
                """, DriverTools.COMPAT.resolve("win").toString(), temp.resolve("instance").toString());
        assertEquals(0, result.exit(), result.output());
        assertTrue(result.output().contains("language PASS"), result.output());
    }

    /**
     * A sweep whose writer died on its own — a reboot; os._exit skips the finally as taskkill /F does — gives the
     * player back only the lang line it changed, never the rest of a newer file.
