# Compatibility verification

The current compatibility plan has four implementation phases after this tooling phase.
Keep the same popular and random sets for each before/after comparison. A successful boot
alone does not prove that a mod's features ran.

## Inputs and transport

`../patch-dynamic-torches.py <original.jar> --output <fixed.jar>` repairs Dynamic Torches 5.4's legacy
`type` entity-predicate key to `entity_type` for Minecraft 26.2. It writes a separate jar and preserves every
other entry; keep the original outside the active mods directory when installing the repaired copy. Run
`python3 -m unittest discover -s run/compat -p test_dynamic_torches_patch.py` for archive preservation,
idempotence and refusal checks. World validation must also prove `dt:tag` loads and a torch item receives
the `dt.lit` tag; a JSON rewrite alone is not functional acceptance.

Use Python 3 (standard library only), Bash, and the staged game/carrier jars. Configure
`WINSH` and `WINFILE` as the executable commands for the existing Windows shell/file
transports. Shell aliases are not inherited by scripts. `lib-compat.sh` parses these
commands without `eval`, limits each call to 240 seconds, and checks a PowerShell success
sentinel. Do not put credentials in reports or committed files.

Set `FORBRIC_MC` to the Windows Minecraft root and `FORBRIC_VERSION` to its installed
profile id. `FORBRIC_INSTANCE` optionally selects a dedicated test instance; otherwise
the profile directory is used. `FORBRIC_WORLD` selects the generated save. Preserve the
launcher-created native directory and `options.txt`. `FORBRIC_PYTHON` selects the remote
Python executable and must name the real interpreter, never a launcher shim: a shim
re-executes a different process, so the job publishes a pid this run never started and
cannot vouch for, which is reported by name instead of retried. A Python Manager shim
names its interpreter in `<command>.__target__`. All five Windows entry points accept `--print-config` on Mac
without launching, reading the Windows disk, or importing Windows-only APIs.

## Select the packs

`pick_mods.py <mods-dir> --slugs a=forge,b=fabric --resolve-only` resolves named builds
before downloading. The requested loader is mandatory: a NeoForge build is not a
MinecraftForge substitute. Missing versions print `UNAVAILABLE`. Keep that result in
the report and choose a same-purpose replacement before accepting the popular set.
Use `MODRINTH_API` to select the API endpoint (also the local HTTP fixture seam).

For the next random set, set `SEED=20260919`, `WANT_FABRIC=26`, `WANT_NEO=26`,
`WANT_FORGE=18`, and `EXCLUDE_MANIFEST` to the previous set's manifest. Preserve the
resolved manifest, including dependencies and actual loaders, with the run evidence.

`abi-audit.py` checks class references against explicitly supplied carrier/game jars;
`field-drift.py` compares vanilla and merged field descriptors and reports affected
guest jars. `fapi-usage.py` scans class references, including `META-INF/jars`, to prove
that candidates actually call a named set of symbols — by default the Fabric loot/model
surfaces, and with `--preset` / `--symbols` any other set, which is how "who is actually
waiting on this Forge event" gets answered from bytecode instead of from `javap` notes in
a javadoc. `--list-presets` prints the named sets. `hook-worklist.sh` goes the other way round: it
censuses which of a carrier's event hooks the merged base still calls, then joins the dead
ones to the jars in a mods directory that name them, so the list is ordered by how many
mods notice rather than by whatever order the byte-merge produced. `repair-drift.sh`
replays the compat transformer's claim ledger against a CANDIDATE game build and names
the repairs that would stop applying, which is the question a carrier bump asks and that
nothing answered before a player did. `control-diff.sh` answers the question that has
been settled by argument until now — is this symptom Forbric's — by booting the same
Fabric mods on a native Fabric server and on Forbric and saying which log it appears in:
FORBRIC-ONLY, BOTH, NATIVE-ONLY or NEITHER, and INCONCLUSIVE when an arm did not start.
Run each with `--help` for its argument list. API usage must be proved from the downloaded candidate
jar before treating the selection as final; metadata resolution alone cannot prove it.

## Carpet rule and event verification

`carpet-gate.py --carpet <fabric-carpet-26.2+v260616.jar> --staged-root <forbric-loader/run>`
compiles the Carpet probe and runs 27 behavior checks in isolated dedicated-server worlds. The baseline
runs with `forbric.carpetMixins=off` and must fail exactly the 16 checks that need the adapter (fill shape
updates, a direct `Level.setBlock` under `impendingFillSkipUpdates` for the neighbour-update redirect, renewable
blackstone and deepslate on, both Scarpet events and their native-Fabric order); vanilla's own lava/water reactions
pass there, since placement no longer depends on the adapter (`FluidInteractionsInjector`), and the summary lists
Carpet and base-fluid failures separately. The fixed run uses strict compatibility policy and must pass every
behavior check with no confirmed Carpet losses, and none of the five adapted mixins may be left suspected or
"applies only partially" (the baseline must still show them, so the check can fail). Both runs
must save and shut down normally. Reports, logs, test worlds and input hashes stay under the printed
output directory; `--output` selects a new directory explicitly. See
[the Carpet probe instructions](../../canary/carpet/README.md) for prerequisites and coverage.

## Vanilla fluid parity

`fluid-parity-gate.py [--output DIR] [--unfixed]` runs one datapack, unchanged, on a vanilla 26.2 dedicated server
(`launch-vanilla-server.sh`, the player's own jar) and on the kernel with zero mods (`launch-kernel-server.sh`): lava set
beside water (source and flowing), water set beside lava, lava set on soul soil beside blue ice, lava flowing into a
cell under water, water flowing to lava, lava flowing down into water, and a cobblestone and a basalt generator emptied
every tick for 600 ticks. The scores the console prints and the blocks in the saved region files must be identical on
both sides, and vanilla itself must show every reaction (so a scenario that measured nothing cannot pass). `--unfixed`
runs the kernel with `-Dforbric.fluidInteractions=off` and must go RED. Needs Java 25 on `PATH`, like gate M31.

`fluid-parity-gate.py --mods [--native-controls DIR]` is the same comparison for mods' fluid rules, against the loaders
themselves: native NeoForge 26.2.0.88 with a NeoForge canary mod and native MinecraftForge 26.2-65.0.1 with a
MinecraftForge canary mod (`canary/fluid-interactions`, each compiled against its own loader's installed jars only), and
the kernel with both jars. The native images are the ones `native-controls.py prepare` installs (default
`build/native-controls`); each run copies them and never writes into them. On NeoForge a mod's rule runs when a block
next to the liquid changes and never when the liquid is placed; on MinecraftForge it runs on both, and every rule is
tried at one neighbour before the next, so a mod's rule above beats vanilla's water to the east. Every case cell must
match the server of the loader whose entry point the merged game uses there (placement: MinecraftForge's; a neighbour
change: NeoForge's, then MinecraftForge mods' rules at the same neighbour), immediately and 100 ticks later, and the
canaries must report the same firings there; where both rules match one block, exactly one runs. `--mods --unfixed`
must go RED.

## Fabric menu codec verification

`menu-codec-gate.py --farmers-delight <FarmersDelight-26.2-3.6.26+refabricated.jar>`
compiles the menu probe and opens Farmer's Delight's cooking pot through a real `ServerboundUseItemOnPacket` in
isolated dedicated-server worlds with Fabric API 0.155.2+26.2. The baseline runs with
`forbric.wrapperEntryEvents=off` and must reproduce "Codec for farmersdelight:cooking_pot is not registered!";
the fixed run uses strict compatibility policy and must record the codec, open the menu and send fabric-menu-api's
`open_screen` payload, with no confirmed Farmer's Delight or fabric-menu-api losses. Both runs
must save and shut down normally. See [the menu probe instructions](../../canary/menu-codec/README.md).

## Run and collect

1. Build the four staged artifacts and canaries. Do not run a gate while a Windows
   sweep is using those artifacts; gates rebuild the kernel jar in place.
2. Use `push-and-run.sh --label <label> --mods <mods-dir> --manifest <manifest.json>`.
   Preview with `--dry-run --version-json <installed-profile.json>`. The dry run names
   all four artifact uploads, sanitized mod names, PID stop, cleanup, and evidence.
3. Stop only PIDs read from `.forbric-sweep.pid` / `.forbric-gate.pid`, including the
   server subdirectory's gate file. Never kill all Java/Python/game processes by name.
   Those PIDs include the client and bisect drivers, so a stop also skips their own restore of the player's
   `options.txt`. The stop does that restore itself: before its kill it notes whether the process that wrote
   `options.txt.forbric-sweep` is alive, and after the kill it puts the file back from that record (step 6).
4. Clean the explicit test-state children: `config`, `mods`, `saves`, `logs`,
   `.forbric-kernel`, `.mixin.out`, `.fabric`, `crash-reports`, `screenshots`,
   `server-gen`, `quickPlay`, `resourcepacks`, `defaultconfigs`, `.cache`,
   `.physics_mod_cache`, `replay_recordings`, and the three console logs. A recording a killed client left
   unfinished makes ReplayMod hold the title screen on its recovery prompt, and quick-play waits behind it
   (72 s in one Mac run). Preserve natives, `options.txt`,
   backup ZIPs, launcher metadata and PCL files. `mods-all` is refreshed only from the
   new pack and serves as the source for a later subset test.
   The kernel jar is built from the working tree (`./gradlew --offline jar`) before anything is staged, so the
   commit report.md names is the code that ran; `--no-build` stages `build/libs` as it is.
   Through a relayed tunnel that throttles or stalls (a UU Remote port forward stalls for minutes after tens of
   megabytes), pass `--remote-mods`: only the manifest crosses the transport and `win/fetch-mods.py` downloads each
   jar on the Windows side from its own URL, verifying size and SHA-1 against the manifest; an upload whose remote
   SHA-256 already matches is skipped either way. `COMPAT_CALL_TIMEOUT` (default 240) raises the per-call ceiling
   for such a tunnel; whether a stalled call may be repeated is the `WINSH`/`WINFILE` command's own decision.
5. `version-json-sync.py` updates SHA-1/size for exactly four supplied `group:artifact`
   pairs. It keeps library order and all other metadata. A missing pair is an error.
   Upload the artifacts and refreshed profile, then the driver tools and mod archive.
   Windows-illegal jar characters are replaced with `_`; collisions fail before upload.
6. `win/run-server-test.py` drives `win/forbric-server.py` through world generation,
   ticks, save, and clean stop, then copies the save for the client. Both job drivers
   distinguish a process that is still working from one that has stopped: `--boot-timeout`
   (900s) and `--run-timeout` (1200s) are ceilings for the former, `--boot-stall` (120s,
   `BOOT_STALL`) and `--stall` (300s, `CLIENT_STALL`) bound the silence of the latter,
   measured from the last line it printed. The server's stall applies only before `Done` —
   after that the tick soak is quiet by design. Sixteen recorded sweeps put the largest
   silence of a boot that reached `Done` at 8 seconds, against 900 spent waiting on ones
   that never would; two such runs cost 820s and 1615s.
   The soak is `--tick-seconds` (60s, `TICK_SECONDS`), and every second of it now ticks: the
   properties written for the run set `pause-when-empty-seconds=0`, without which vanilla
   pauses a player-less server 60 seconds after `Done` and returns from `tickServer` before
   `tickCount++` and before `fireServerTickPre`. The old 90s soak was 60s of simulation and
   30s of a paused JVM. Keep that property whatever `--tick-seconds` becomes — a boot reaching
   `Done` is still not the claim this sweep makes, and a soak that is not ticking makes no
   claim at all. `win/prepare-world.py`
   sets only the copied test save's `Data/confirmedExperimentalSettings` byte to 1,
   acknowledging the carrier's experimental-world prompt without changing lifecycle,
   datapacks or terrain. The source server save is untouched; `--check` is read only. `win/run-client-test.py`
   drives `win/forbric-launch.py` into it, requests Minecraft's own screenshot at tick
   100, and requires a clean disconnect. Both launchers resolve the installed version
   JSON rather than a developer classpath. Vanilla runs quick-play only after its chain of first-run screens, so
   before the client starts `common.FIRST_RUN_SEEN` marks a mod's own first-run screen as already dismissed (today
   wover-ui's BetterX welcome) — the state of a player who has clicked through it once. The client also plays one
   language whoever runs it: `common.sweep_language` sets options.txt's `lang:` to `en_us` for the run.
   `push-and-run --client-lang` (default `en_us`, also for an empty `FORBRIC_LANG`) passes another code to the
   driver's `--lang`; `player` leaves the player's language as it is. report.md records the language the client
   played. The language decides which assets every mod loads, and the Windows profile's zh_cn is what killed
   sweep90-win-r7c: Axiom 6.1.3's bundled Dear ImGui keeps a pointer into font arrays the JVM may move, and its CJK
   fonts are big enough to trigger that GC. Native Fabric with only Axiom and fabric-api asserts the same way once a
   GC lands between the add and the build (forced, or under `-XX:+UseSerialGC -Xmn16m`); under default G1 that
   minimal native pack did not crash in the runs recorded. en_us narrows the race, it does not close it.
   `options.txt` is the player's own file, and Minecraft rewrites all of it while the client loads (it saves
   `startedCleanly:false` at startup and `true` only once loading finishes; a false one makes the player's next start
   reset its fullscreen mode). What the client and bisect drivers promise about it, in every `--lang`:
   - Before anything is written, the driver keeps `options.txt.forbric-sweep`: the player's file as it was (or that
     there was none), the `lang:` it wrote and the one it replaced, and its own pid and process start time.
   - The run ends, normally or with an exception: the player's bytes go back exactly, over the client's rewrite too,
     or the file the client wrote is removed when the player had none.
   - The stop (step 3) kills a running or loading client: the stop saw the record's writer alive before its kill,
     so after the kill it puts back the same exact bytes, or removes the file, as the driver would have.
   - The writer died any other way, a reboot above all: the next stop or client or bisect run finds a record whose
     writer is gone (pid and start time, so a pid Windows has handed on does not count). The player may have played
     since, so only the `lang:` line goes back (under `player` nothing does), and only while every `lang:` line
     still names the sweep's language; a line the sweep added is taken out. Everything else stays as the file has
     it, Minecraft's rewrite from the killed run included (`startedCleanly:false`, and whatever else it saved), and
     so does a file the client created where the player had none.
   - A second client or bisect run on the same instance refuses to start while the first one's writer is alive,
     naming its pid and the record, and touches nothing of options.txt.
   - A record that cannot be read is acted on by nobody: the driver, or the stop after its kill, fails naming the
     file to delete once `options.txt` has been checked by hand.
   - Not covered: anything else writing `options.txt` during a run (the player starting the same instance) is
     overwritten by the restore. Two copies of the stop at once (`winsh` re-sending one after a relay stall) can race
     each other, and the player then gets the language back but may keep the killed client's rewrite. A restore that
     itself fails (the file held open by something else) leaves the record, which is then undone like a reboot's.
   The stop's side is PowerShell; `common.note_sweep_writer` / `common.restore_after_stop` are its Python twin, and
   the tests run that twin. `win/common.py` owns shared arguments, PID recording, launch resolution, frame
   inspection, and F2 fallback.
7. Long jobs run with `Start-Process` (no stream redirection: the job writes its own
   logs, so it inherits nothing of the remote shell and the start returns at once)
   and a saved PID/status handle. Poll that same
   handle; an observation timeout is not a terminal job and never authorizes starting
   another copy. Re-inspect the handle after a connection interruption. Each remote
   command remains below 240 seconds even when the game takes tens of minutes.
8. Collect logs, fresh Minecraft PNGs, saved regions and `load-report.txt` into the
   run directory. `assert.sh` checks the common client observations; `ASSERT_EXTRA`
   may name a pack-specific Bash assertion file using `ck`/`abs`. Missing/empty logs fail.
   `frame-verdict.py` reports `DREW`, `BLACK`, or `UNSUPPORTED`; only `DREW` is success.
   Desktop/GDI captures cannot replace a Minecraft screenshot. `region-probe.py`
   reports chunks containing each needle, plus explicit unreadable lz4/custom/corrupt
   counts. `--dungeons` reports spawner/mossy-cobblestone co-occurrence, not an exact
   count of dungeon structures. Require readable chunks and at least one matching
   chunk; record every unreadable chunk.
   `world-parity.py` is not part of the sweep: gate-m31 runs it over two saved
   overworlds — one written by a pure-vanilla server, one by the kernel with zero mods —
   and it prints, per facet, how many common chunks differ. Only `differ biomes`,
   `differ structures` and `differ spawner_mobs` (the mob of spawners standing at the same
   position in both worlds) may be asserted on. Vanilla does not reproduce itself at the block
   level, so `differ blocks`, `differ heightmaps` and `differ block_entities` are evidence
   and nothing more, and so is `differ spawner_positions`: a mineshaft corridor's spawner goes
   to whichever chunk generated first. A comparison over two absent or half-generated worlds
   reports zero differences, so the `chunks:` and `full:` counts must be checked before any
   zero counts. `test_world_parity.py` checks which spawner differences count; gate-m0 runs it.
9. `push-and-run.py` implements the shell entry point's orchestration and writes
   `report.md` using `report-template.md`, with commit, manifest, phase results, log
   assertions, frame verdict, region evidence and named load-report failures. Retain
   failed baselines as evidence. Compare each field, including new DEGRADED reasons,
   with the corresponding baseline; a boot reaching Done is insufficient.

## Investigate a failure

Use `push-and-run.sh --label <label> --bisect <subset.txt>` to run one named subset
against the staged `mods-all` and world. `win/bisect.py` uses the same fresh-frame
classifier, never a quiet log as proof of a rendered client. Halve the suspect subset
and repeat to isolate the failure, keeping each subset and verdict. Preserve required
dependencies. Missing/unsupported screenshots are unproven, not a green subset.

`--quarantine <jar>` moves one explicitly named jar out of `mods`; record its actual
failure and the subset evidence. Do not hide quarantined jars when comparing totals.
Replacements must be labelled with their actual project and loader in the manifest.

## Gates and cadence

`gates-all.sh` discovers every `run/gate-m*.sh` and reports in numerical order, including
network and GUI gates. `--list` is the actual glob. Use repeated `--skip <script.sh>`
only when intentional; every skip prints a RESULT line.
Logs and one-line results go to `build/gates/`, with a `summary.txt`.

It runs several gates at once: `-j auto` (the default) sizes the pool from RAM and cores,
`-j 1` is the old strictly-sequential run, `--mem-budget MB` caps what the running set may
claim. `gates-parallel.py` does the scheduling and is where the reasoning lives. Each gate
declares itself in one line near its top:

    # GATE-PARALLEL: rundirs=server-kernel,canary mem=1500

`rundirs` names what the gate owns while it runs — two gates naming the same one are never
co-scheduled — and `mem` is what it costs. A gate WITHOUT that line runs alone, and the run
says so in the progress log; a new gate is slow rather than silently unsound.

A gate that wants a shared fixture to itself declares `clone=<dir>:<ENV_VAR>` instead, and the
scheduler points that variable at a private copy under `run/.gate-clones/`. Four gates want
`run/client-merged-pack`, and serialising them left the last two minutes of a sweep with one
gate in it; `cp -Rc` clones that 434 MB install in 0.17s and shares its blocks until written,
so the four copies cost no disk and no wait. On a filesystem without clones this falls back to
a reflink copy and then to a real one.

`-j auto` sizes the pool at one slot per two cores. **Cores, not memory, is the bound**: the
sweep peaks at 5.5 GB of game JVMs however wide it runs, but at `-j 7` on ten cores the gates
are starved enough that time-based assertions fail — `gate-m19` went red on `await_server`'s
"still alive 20s after announcing its stop", which is a real check for a leaked non-daemon
thread and is not to be relaxed to suit a scheduler. `-j 4` and `-j 5` are green.

Ports are per concurrent SLOT, not per gate: slot *i* gets `25700 + 10i`, and `GATE_PORT`,
`M12_PORT`…`M16_PORT`, `M28_PORT` and `M32_PORT` are exported to the gate from that block. A
`GATE_PORT` already in the environment becomes the base instead. This is not tidiness: every
gate that starts a server writes a port into `server.properties`, and the loser of a port race
prints `FAILED TO BIND TO PORT`, writes a crash report, and then still prints `Stopping server`
and `All dimensions are saved`. Measured on `gate-m1` with its port held by another process: both
shutdown checks passed, and the gate went red on "server reached Done" without a word about a
port, which reads as the kernel failing to boot. The old default, 25599, is also `gate-m12`'s
own `M12_PORT` default, which is exactly that collision.

So a lost port is now named. `lib.sh`'s `port_was_free` fails with the port when a server log
says it lost it. `await_server` runs it on every server it waits for; `gate-m12`…`m16`, which
give up on a server that never reaches Done before they ever wait for it, run it on that exit;
and the gates that start their server through `evidence.py` call it, or its Python equivalent,
on that server's log — `gate-m39` before `set -e` ends it on the failed run.

The knob list is hand-kept, and a gate reading a name it does not contain gets nothing: it keeps
its own literal, and the slot where that literal meets an exported one is the race above.
`M32_PORT` was missing, and `gate-m32-savedrop.sh`'s fallback was slot 10's `M16_PORT`. Slot 10
exists only from `-j 11` up — at least 22 cores and about 30 GB under `-j auto` — and then
`gate-m16` and `gate-m32` could take one port, and whichever lost it went red. `gate-m36`,
`gate-m37` and `gate-m38` wrote their port into `server.properties` as a literal, so a second
sweep started with its own `GATE_PORT` still met them there; they read `GATE_PORT` now.
`python3 run/compat/test_gate_ports.py` holds every gate to these rules: a `*_PORT` it reads from
the environment is one the scheduler exports; no gate writes a literal port; a gate that starts a
server calls the lost-port check; and a "never reached Done" exit calls it before it leaves. It
reads text, so it holds the call and that one shared exit, not every path through a gate.

