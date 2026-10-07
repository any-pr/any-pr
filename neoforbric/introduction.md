# Forbric — architecture and internals

English | [简体中文](introduction.zh-CN.md)

For mod and loader developers. This document is precise rather than gentle: it states what Forbric does, in the
order it does it, naming the real types and files. It describes the **`main` branch**, not a release; for what a
release contains and how a player installs it, read the [README](README.md).

> **Which code this describes.** The repository holds two generations. `forbric-kernel/` — the *sovereign
> kernel* — is what `main` ships and what `forbric-kernel-installer/` installs; it is the subject of this document.
> `forbric-loader/` is the first generation (the *weld*: real fabric-loader/Knot as host, with FML and
> FancyModLoader driven alongside it). It no longer runs in an installed instance, but it still builds the tools
> and staged artifacts the kernel is built and tested against — see [§14](#14-what-forbric-loader-is-still-for).

Terminology:

| Term | Meaning here |
| --- | --- |
| **ecosystem** | Fabric, NeoForge — `net.forbric.api.Ecosystem.FABRIC` / `NEOFORGE` |
| **Forge family** | the FML lineage NeoForge belongs to: `mods.toml` manifests, FML, the event bus. One runtime, one manifest (`META-INF/neoforge.mods.toml`), one event bus |
| **patched base** | `patched-mc-neoforge-26.2.jar`: NeoForge's own patched 26.2 — vanilla carrying NeoForge's patches, the one game Forbric runs |
| **carrier** | `neoforge-runtime.jar`, NeoForge's runtime jar, loaded as a passive ABI provider — its classes exist, its loader lifecycle never runs |
| **boot side / game side** | code loaded by the system class loader vs. code defined by `ForbricClassLoader` |
| **guest** | anything belonging to a third-party mod (guest mixin, guest jar) |

---

## 1. The problem

Two loaders assume they own the process. Running their mods together breaks in five independent ways, and
every part of the kernel answers one of them.

1. **Launch ownership.** Fabric starts Knot; NeoForge starts ModLauncher/BootstrapLauncher with
   a JPMS module layer. Two transforming class loaders means two definitions of every game class.
2. **Vanilla and patched bodies.** NeoForge patches the *same* `net.minecraft` classes a Fabric mod was
   written against, and a JVM can hold one version of each class. Something has to keep the Fabric mods'
   expectations working on the patched body.
3. **Lifecycles and registration windows.** Each ecosystem has its own phases, its own bus, its own window in
   which registries are writable, and its own idea of when the freeze happens.
4. **Visibility.** Each loader keeps its own mod list. A mod asking its own loader "is Sodium installed?" or "which
   platform am I on?" gets an answer that is true for a single-loader instance and wrong here.
5. **Guest bytecode written for a different game.** A Fabric mixin was written against vanilla bytecode; on
   the patched base, anchors have moved, methods have been split, fields re-typed, lambdas renumbered,
   superclasses swapped.

Namespace is *not* on this list for 26.2: the game ships Mojmap names, NeoForge 26.2 mods are Mojmap-compiled,
and so are Fabric's (`KernelMappingResolver`'s javadoc records a constant-pool scan of fabric-api and Jade finding
no intermediary symbols). The kernel runs identity mapping: `TransformContext(…, "named")`, and
`KernelMappingResolver` answers every lookup with its input.

Nor are mod APIs. Forbric does not re-implement the Fabric API or the NeoForge APIs: a mod calls the
genuine Fabric API mod it installed, and the genuine NeoForge classes from the carrier. What the
kernel owns is the part a loader owns — class loading, discovery, the lifecycle, the registration window, and
Fabric Loader's own API (§5.1) — plus the repairs the patched base makes necessary. One of those repairs
reproduces behaviour instead of calling it: NeoForge's coremod rewrites, which the kernel performs itself (§5.2).

## 2. Shape of the solution

One JVM, one transforming class loader, one lifecycle, one registry freeze. No genuine loader lifecycle ever
boots: there is no Knot, no ModLauncher, no FancyModLoader discovery, no module layer.

```
system class loader  (BOOT side)
 ├─ forbric-kernel.jar            net.forbric.kernel.{boot,classloading,discovery,fabric,transform,mixin,
 │                                 access,metadata,mapping,interop,ui,util,soak}, net.forbric.api,
 │                                 vendored net.fabricmc.api / net.fabricmc.loader surface
 └─ its dependencies               ASM, sponge-mixin, SAT4J, NightConfig, tiny-remapper, class-tweaker, mapping-io
     │  new ForbricClassLoader(owned, parent)
     ▼
ForbricClassLoader  (GAME side — the only loader that defines game/ecosystem classes)
 owned jars, in this order (KernelOwnedClasspath.compose):
   1. patched-mc-neoforge-26.2.jar         --gameJar
   2. neoforge-runtime.jar                  --runtimeJar   (the carrier)
   3. Minecraft's own libraries             --libraryPath  (owned: mods mixin into DataFixerUpper & co.)
   4. kernel-bundled: mixinextras-fabric.jar, forbric-kernel-runtime.jar   (extracted to .forbric-kernel/lib/)
   5. guest mod jars: NeoForge, then Fabric, nested jars included; newest version first within one mod id
```

### 2.1 Boot side vs game side

`forbric-kernel.jar` is parent-loaded and names no game type. Everything that references `net.minecraft.*`,
`net.neoforged.*` or `net.fabricmc.fabric.*` lives in `src/runtime/java`
(package `net.forbric.kernel.runtime` and its `soak`/`transfer` subpackages, 130 files), is compiled `compileOnly` against the staged game artifacts,
packaged as `forbric-kernel-runtime.jar`, nested at `META-INF/jars/` inside the boot jar, extracted at launch by
`KernelBundledJars` and loaded as an owned jar.

The boot side reaches the game side by *string* (`Class.forName`, `getMethod`, ASM owner names).
`KernelRuntimeClasses` is the registry of every such string, and `KernelRuntimeClasses.verify(loader)` loads the
whole seam through the finished pipeline at boot, so a boot jar built without its game half, or a renamed
game-side method, fails at the top of the log rather than in the middle of a mod's construction.
One game-side class, `net.forbric.kernel.runtime.KernelHudLayer`, is still emitted at runtime with an ASM
`ClassWriter` (by `KernelHudBridge`); everything else on the game side is compiled.

### 2.2 `ForbricClassLoader` and the delegation table

`classloading.ForbricClassLoader` is a flat, JPMS-free `URLClassLoader`. `loadClass` consults
`DelegationPolicy`, in order:

- **ALWAYS_PARENT** — the JDK, ASM (`org.objectweb.asm.`), Mixin (`org.spongepowered.asm.`), log4j/slf4j,
  NightConfig (a carrier bundles an old unshaded copy that would otherwise win child-first),
  `net.fabricmc.api.`, `net.fabricmc.loader.api.`, the Fabric Loader internals in `FabricLoaderInternals` (by exact
  name), `net.forbric.api.`, and the kernel's boot packages (`boot`, `classloading`, `transform`, `mixin`, `access`,
  `mapping`, `metadata`, `discovery`, `fabric`, `util`, `interop`). Exactly one copy per JVM.
- **ALWAYS_GAME** — `net.minecraft.`, `com.mojang.blaze3d.`, `net.neoforged.`,
  `net.fabricmc.fabric.`, `net.forbric.kernel.runtime.`, `com.llamalad7.mixinextras.` and Mixin's synthetic
  package. Defined here or not at all.
- **otherwise child-first**: defined here if an owned jar has it, else the parent.

`tryDefineGameClass` reads the bytes, runs the pre-Mixin chain (`setTransformer`), then the Mixin stage
(`setMixinTransformer`), then `defineClass` with a real `ProtectionDomain` (the jar as code source — mods such as
JourneyMap and spark locate their own jar through it). Other duties of the loader:

- **Pre-Mixin bytes.** `getPreMixinClassBytes` serves Mixin the post-chain, pre-weave bytes; serving woven bytes
  would make the weaver re-weave its output.
- **Generated classes.** `putGeneratedClass` holds classes synthesized by transformers (class-tweaker enum
  extensions); a `null` read with no generated entry is Mixin's cue to synthesize its own.
- **Rescue jars.** `setRescueJars` — the jars cross-jar arbitration superseded — are consulted *only* when no
  owned jar has the class, so they cannot shadow the winner (§4.3).
- **Jar families.** `setJarFamilies` records which ecosystem each mod jar was arbitrated to; `familyOfClass` /
  `familyOfResource` feed the environment stripper and the loader-probe rewriter.
- **Re-entrant definition.** A guest config plugin constructed during Mixin's first `select()` can load the class
  currently being defined; `define` recovers the already-completed definition instead of failing with a
  duplicate-definition `LinkageError`.
- **Package manifests.** Packages are defined with the owning jar's manifest attributes, because genuine FML reads
  `Package.getImplementationVersion()`.

### 2.3 The two game artifacts

Neither is in the repository or in any Forbric download: each embeds Mojang or NeoForge
code. They are built on the machine that runs them — by the installer for players (§13), by
`forbric-loader/run/` scripts for developers (§14).

| Artifact | What it is |
| --- | --- |
| `patched-mc-neoforge-26.2.jar` | vanilla 26.2 plus NeoForge's own patches, produced by NeoFormRuntime (`NfrtRunner`, result `gameJarNoRecomp`) — NeoForge's own game jar |
| `neoforge-runtime.jar` | NeoForge's `-universal` jar plus the libraries its userdev config declares, merged into one jar (`NeoForgeRuntimeBuilder`) |

The carrier is *passive*: its classes are defined and its event bus used, but its loader's
discovery, sorting and lifecycle never run. The kernel supplies only the identity its code queries
(`PassiveSeeder`, §3.2).

## 3. Boot order

### 3.1 Entry

`boot.KernelClientLaunch.main` and `boot.KernelServerLaunch.main` are one line each:

```java
int code = CompatibilityLaunchBoundary.run(() -> KernelBoot.launch(KernelBoot.Side.CLIENT, args));
if (code != 0) System.exit(code);
```

`CompatibilityLaunchBoundary` is the only place a compatibility refusal becomes a process exit (code `78`,
§12.4), and the only place a refused install does (code `2`, §3.2 step 0). Any other throwable leaving the boot is
logged there — message and trace, into `latest.log` — before it is rethrown, because a launcher shows that file and
not stderr. `KernelBoot.launch` consumes `--gameJar`, `--runtimeJar` (repeatable, and one value may carry several jars
joined by the path separator — launchers such as PCL2 keep only the last occurrence of a repeated flag) and
`--libraryPath`; everything else, and everything after `--`, is forwarded to the game's `Main.main`. The dedicated
server rejects `--gameDir`, so `KernelBoot` strips it on that side. The game version is read from the base jar's
`version.json` (fallback `26.2`).

### 3.2 `KernelBoot.launch`, in order

0. **Launch inputs** — `LaunchInputCheck.require(gameJars, runtimeJars)`, by content and before anything is read
   out of them: the base's `Block` must implement NeoForge's extension interface (the patched base), and the
   `--runtimeJar` must carry the NeoForge runtime completely (loader SPI, `ModContainer`, `FMLLoader`,
   `FMLEnvironment`, its own `neoforge.mods.toml`). A failure logs each problem and the fix (rerun the installer with
   *Built artifacts* empty) and stops with exit code `2`; `-Dforbric.launchInputCheck=off` only warns. Issue #13:
   empty "runtime" jars used to pass every later step with an empty answer and die in `KernelRuntimeClasses.verify`
   on stderr, leaving five INFO lines in `latest.log`. The check reads names, not every class; the class javadoc lists
   what it leaves to later steps.
1. **Cross-jar arbitration pre-scan** — `DuplicateModArbiter.arbitrate(mods/, envType)` inventories every root and
   nested candidate and fixes one selection before either ecosystem's discovery runs (§4.3).
2. **Carrier versions** — `EcosystemVersions.record(runtimeJars)`, so a mod whose `versionRange` the carrier
   cannot satisfy is reported as it is discovered.
3. **NeoForge discovery** — every `mods/*.jar` with a `META-INF/neoforge.mods.toml` manifest, then the JarJar
   children (`META-INF/jarjar/`) the arbitration plan selected, taken from the plan's content-addressed extraction under
   `.forbric-kernel/candidates/` (the legacy extractor, writing `.forbric-kernel/jarjar/`, runs only when
   arbitration is off). A jar whose only manifest is `META-INF/mods.toml` is a MinecraftForge mod; it is
   reported as unsupported and skipped.
4. **Presence** — `ModPresence.publishForgeFamily(…)` before the Fabric side is built, because the Fabric side
   reads it back.
5. **Fabric discovery** — `KernelFabricEcosystem.scan`, then a second arbitration pass over the union of nested
   jars (`DuplicateModArbiter.arbitrateNested`); losers are dropped from both lists.
6. **Mixin config declarations** — NeoForge configs from mod jars, nested mod jars *and the carrier*
   (NeoForge's own `neoforge.mixins.json`).
7. **Fabric ecosystem build** — `KernelFabricEcosystem.build` creates the `FabricLoader` view (§5.1).
8. **Owned classpath** — `KernelOwnedClasspath.compose` (order in §2).
9. **Static audits** over every guest jar, before anything is loaded: `PortingLayerAudit` (Fabric jars shipping
   their own `net.neoforged.*`), `FabricApiModuleLossAudit`, `FieldDriftAudit`,
   `MergedBaseUncalledMethods.scanGuests`, `AbiLinkAudit` (NeoForge classes named by a jar that exist in no
   carrier, base or installed jar).
10. **Class loader** — `new ForbricClassLoader(owned, bootLoader)`, rescue jars, jar families,
    `LoaderProbePolicy.bindGuestLoader`, `KernelFabricLauncher.install` (a mod's `addToClassPath` lands here).
11. **Transform chain** — 91 `chain.register(…)` call sites (§6).
12. `loader.setTransformer((name, bytes) -> chain.applyBeforeMixin(name, bytes, ctx))`; the context class loader
    becomes the game loader;
    `KernelLifecycle`, `KernelHudBridge`, `LootTableEventDispatch` are bound; `KernelLoadReport` and
    `CrashAttribution` get the run directory (and their shutdown hooks).
13. **Loader identity, before Mixin** — `PassiveSeeder.seedNeoForgePaths`, `seedNeoForgeLoader`. This must
    precede the first class that passes through the Mixin transformer, because Mixin constructs every guest
    `IMixinConfigPlugin` then, and plugins read `FMLPaths`/`FMLLoader` in their `<clinit>`.
14. **Mixin** — Fabric configs first, NeoForge configs appended (§7.1); `MixinConfigOwners.publish` before
    registration; `KernelMixinBootstrap.init`.
15. `PassiveSeeder.reportDependencies()` — after Mixin, because half of what it reports (mixins meant for another
    mod that did not attach) is recorded while Mixin parses configs.
16. `KernelRuntimeClasses.verify(loader)` — the kernel's own game side, through the finished pipeline.
17. `PassiveSeeder.seedAll` — NeoForge `FMLLoader`, `ModList`, paths.
18. Audit reports (among them `MixinOverlapLint`, §7.6), `KernelLoadReport.writeEvidence()`, then
    `CompatibilityDecision.requireContinuation(isClient)` — the pre-game decision point (§12.4).
19. `KernelFabricEcosystem.runPreLaunch()` — Fabric `preLaunch` entrypoints, after Mixin, before any game class.
20. Load the entry class (`net.minecraft.server.dedicated.DedicatedServer` / `…client.gui.screens.TitleScreen`
    is the census landmark; the invoked class is the game's `Main`). If `LifecycleHookInjector` did not find its
    trigger, **the kernel refuses to boot** (`missedRequiredExcision()`).
21. `Main.main(gameArgs)` — vanilla boot, with the genuine loader trigger redirected.

### 3.3 The redirected trigger

The entry points are NeoForge's own. `transform.LifecycleHookInjector` retargets (owner + name, same
descriptor) one `invokestatic` in each:

| Side | Genuine call in the patched base | Now calls |
| --- | --- | --- |
| server | `net.neoforged.neoforge.server.loading.ServerModLoader.load(Z)V` in `net.minecraft.server.Main.main`, after `Bootstrap.bootStrap()` | `KernelLifecycle.onServerModLoading(boolean)`, followed by Fabric's `Hooks.startServer(null, null)` marker (`-Dforbric.fabricHooks=off` omits it) |
| client | `net.neoforged.neoforge.client.loading.ClientModLoader.begin()V` in `net.minecraft.client.main.Main.main`, after `Bootstrap.validate()`, before `new Minecraft` | `KernelLifecycle.onClientModLoading()` |

The client's later calls into NeoForge's `ClientModLoader` (`finish`, `completeModLoading`) are stubbed by
`MethodBodyNeuter`; `setupModResourcePacks` is redirected to `KernelLifecycle.onClientResourcePacks` instead
(§9.2).

On the client the same injector also makes `Main.logEarlyException` call `KernelLifecycle.onEarlyStartupFailure`
first. That is vanilla's handler for the first three steps of `Main.main` (detecting the version, building and
running the argument parser): it prints to stderr and `main` exits (249, 252, 251) without throwing, so without the
hook the error that ended the game never reached `latest.log`.

### 3.4 The native registration window — `KernelLifecycle.driveNativeRegistration`

Both sides run the same steps (comments in the source number them):

- **0** wire the sided executors; on the client, load the carrier's built-in translations (`CarrierLanguages`).
- **1** register NeoForge's baseline registries (`PassiveSeeder.seedNeoForgeRegistries`); apply NeoForge's own
  registry modifications (sync flags, callbacks).
- **2** `registerNeoForgeContent` — the window itself:
  1. construct `NeoForgeMod` on a kernel-made bus and container (`KernelModContainerFactory`);
  2. construct every NeoForge `@Mod` (`KernelModLoader.constructMods`, §5.2);
  3. register guest `@EventBusSubscriber` classes (`KernelEventSubscribers.registerAll`);
  4. post `FMLConstructModEvent`;
  5. NeoForge `GameData.vanillaSnapshot`, then **unfreeze**; post `NewRegistryEvent`;
  6. fire `RegisterEvent` per registry on every bus, in NeoForge's registration order;
  7. open `minecraft:root` and run Fabric `main` entrypoints (on the client only when
     `-Dforbric.fabricMainInConstructor=off`; see §3.5);
  8. attribute events, spawn placements, modded game-rule categories, NeoForge's
     tooltip appenders;
  9. `closeRegistrationWindow` (in a `finally`): link block→item, **freeze**, rebuild NeoForge's blockstate→id map,
     re-sort NeoForge's creative tabs.
- **2a–2c3** verify the REGISTRATION bridges; publish the NeoForge baseline in `ModList`; load STARTUP/COMMON
  (and on the client CLIENT) configs; wire the carrier's own `@EventBusSubscriber` classes; install the client
  reload-listener bridge.
- **3a** declare datapack registries (`DataPackRegistryEvent.NewRegistry`, Fabric dynamic registries mirrored both
  ways) — deferred on the client until after the Fabric entrypoints (`DatapackRegistryDeclaration`).
- **3a2** start the game buses (`startGameBuses`) — before the setup phases, because `IEventBus.post` on a bus
  that has not started returns silently.
- **3b** (server) post the setup lifecycle to the NeoForge guest mods (`fireModSetupLifecycle`): common setup,
  dedicated-server setup, NeoForge's `RegistrationEvents.init()` one step at a time (`RegistrationEventSteps` —
  it posts `RegisterCapabilitiesEvent` and `RegisterDataMapTypesEvent`), IMC enqueue/process, load complete; deferred
  work runs between phases on the thread NeoForge uses (`NeoDeferredWork`; failures read back by
  `DeferredWorkFailures`). Then `load-report.txt` is written and `CompatibilityDecision.requireContinuation` is asked
  again — the end-of-loading decision point. On the client every one of these phases, common setup included, is
  deferred to `onNeoClientSetup` (§3.5), because `Minecraft.getInstance()` is still null here.
- **3b2** open configs registered late (from construction or setup).
- **3c** (server) close NeoForge's payload registration phase (`setupNeoForgeNetwork`). It must come after setup:
  mods register payloads from `FMLCommonSetupEvent`.

There is one registration window and one freeze. The "Tags not bound" wall the weld hit (two ecosystems
refreezing in turn) cannot arise.

### 3.5 Client-only hooks inside `Minecraft.<init>`

Fabric and NeoForge need opposite states in the constructor, so the kernel has two anchors:

- `ClientEntrypointHookInjector` → `KernelLifecycle.onClientEntrypoints()`, before `Options` exists: reopen the
  registries, construct NeoForge mods that were held back because they reached for `Minecraft` too early, run
  Fabric `main` then `client` entrypoints (Fabric's `Hooks.startClient`
  order), re-close and re-freeze, open late CLIENT configs, then declare datapack registries. Around that freeze is
  Fabric's registry freeze point: with fabric-registry-sync installed, a Fabric mod's `@Inject` at the HEAD or TAIL of
  `BuiltInRegistries.freeze()`, or just before or after `bootStrap()`'s call of it, runs there
  (`FabricFreezeHookMixinAdapter`), where native Fabric freezes — after the entrypoints, with `Minecraft.getInstance()`
  set. LiquidBounce builds its creative tabs from such an injector; left in `Bootstrap` it found no client and died.
- `NeoClientSetupHookInjector` at the base's `ClientModLoader.finish()` call →
  `KernelLifecycle.onNeoClientSetup()`, after `options` is assigned: verify the CLIENT_INIT bridges, preload the
  client resource manager (mods expect their
  assets readable at client setup; `-Dforbric.clientResourcePreload=off`), then `fireClientSetupLifecycle`: common
  setup, client setup, `RegistrationEvents.init()`, IMC, load complete — with the registries frozen, as on genuine
  NeoForge — then `load-report.txt` and the end-of-loading decision (`requireClientContinuation`), then late
  configs, then close the payload registration phase.

## 4. Discovery and arbitration

### 4.1 Reading manifests

- `discovery.ForbricModDiscoverer` classifies a jar by descriptor — `fabric.mod.json`,
  `META-INF/mods.toml`, `META-INF/neoforge.mods.toml` — and reports *every* manifest a jar carries. Which one
  loads is policy (§4.2). A jar whose only manifest is `META-INF/mods.toml` is a MinecraftForge mod and is
  not supported: Forbric reports it as unsupported and skips it.
- `metadata.forge.ModsTomlParser` (clean-room; NightConfig for TOML lexing) reads NeoForge's
  `META-INF/neoforge.mods.toml` (the mods.toml format), with `ForgeModsToml`,
  `ForgeModEntry`, `ForgeDependency`; `ForgeVersionRangeTranslator` turns Maven ranges into Fabric-style
  predicates, so `net.forbric.api.UnifiedDependency` has one dialect (`VersionPredicate` evaluates it).
  `UnifiedDependency` also keeps the two axes Fabric has no word for: ordering (`BEFORE`/`AFTER`) and side scope.
- `fabric.FabricModMetadataParser` is the full `fabric.mod.json` v1 reader (entrypoints incl. adapter form, `jars`,
  per-side `mixins`, `accessWidener`, `custom`). `fabric.FabricModDiscovery` follows Fabric JiJ (its extraction
  cache is `.forbric-kernel/jij/`); mods whose `environment` excludes the side are skipped, as on Fabric.
- A nested Fabric mod is resolved as fabric-loader 0.19.5's `ModSolver` resolves it (`fabric.NestedFabricRequirements`,
  applied by `NestedCandidateInventory` and by `FabricModDiscovery` when there is no plan): a `depends` on `minecraft`
  or `java` that excludes the running version, or a `breaks` that includes it, leaves it out, and with it any nested
  mod that hard-depends only on left-out ones or that only they bundle. ViaFabric is the case: it nests
  `viafabric-mc26-1` (`minecraft >=26.1 <=26.1.2`) beside `viafabric-mc26-2`, and native loads only
  `viafabric-mc26-2`. Only what a Fabric mod declares in its `jars` is judged. A NeoForge mod's nested
  jar is not, even if all it carries is a `fabric.mod.json`, because Fabric Loader never opens a jar without one. A
  child the parent's `META-INF/jarjar/metadata.json` declares is FML's, which loads it as the parent's library; a jar
  that only sits in `META-INF/jars/` or `META-INF/jarjar/` with no entry in that file is loaded by no native loader,
  and the kernel keeps it because its NeoForge walk has always taken both directories. A jar reached both ways is
  kept, whichever route the walk took first. One departure from native: when a mod the kernel loads hard-requires an
  id and nothing that loads meets the requirement, the left-out copies that meet it are kept, with whatever bundles
  them on the way to a loaded parent, and a WARN `[Forbric/JiJ] nested <id> <version> in <parent> loaded although
  Fabric Loader would leave it out (<reason>): <dependent> requires <id> <range>, and nothing else installed meets
  that` says so. When no installed copy meets it and nothing that loads provides the id at all, every left-out copy
  is kept, as the kernel loaded it before the rule, and the WARN ends `<dependent> requires <id> <range>; no installed
  build meets that, and no other <id> would load` instead. For a mod in `mods/` native refuses to start there; the
  kernel loads such a mod even with a dependency missing, so leaving the provider out would only take that dependency
  away. Every mod that stays left out gets one `[Forbric/JiJ] nested <id> <version> in <parent> left out: <reason>`
  line, including what it bundles: a left-out jar is still opened, so a loaded mod's need for something inside it is
  seen. Jars in `mods/` are never judged; `fabricloader`/`mixinextras` ranges, unreadable ranges and dependencies
  nothing installed meets do not leave anything out. Without a plan (`-Dforbric.crossJarArbitration=off`),
  `FabricModDiscovery` walks `mods/` and Fabric `jars` only, so `KernelBoot.scanFabricMods` hands it what the jars it
  does not read hard-require: the Forge-family mods, and the Fabric manifests of the jars their JarJar carries (no
  Fabric container there, but on the classpath). `-Dforbric.nestedRequirements=off` loads every nested mod again;
  `off:<id>,<id>` exempts only those ids (the kernel does not read Fabric's `config/fabric_loader_dependencies.json`).
- `discovery.ModAnnotationScanner` finds `@Mod` classes by bytecode descriptor; `ModFileScanner` builds a full
  `ModFileScanData` (NeoForge's shape) because JEI, Jade,
  Sophisticated Core and Sodium find their plugins through `ModList.getAllScanData()`.
- `net.forbric.api.DiscoveredMod` is the one mod model.

The `--scan` mode (`boot.Main --scan --mods <dir> --report out.json`) runs discovery alone and writes
deterministic JSON; `run/diff-oracle.sh` cross-checks it against an independent Python reader of the same
manifests.

### 4.2 One jar, several manifests — `MultiLoaderArbiter`

A universal jar ships a manifest and glue class per loader. Unarbitrated, both ecosystems initialise it.
`MultiLoaderArbiter.ownerOf(jar)` picks one by preference — default **NeoForge, Fabric**
(`-Dforbric.multiLoaderPreference=…`). NeoForge first because the game base is NeoForge's own. A jar that
carries a leftover manifest without its implementation is not given to that loader. The jar stays on the
classpath; only its identity is decided.

### 4.3 Two jars, one mod id — `DuplicateModArbiter`

Merging a Fabric pack with a NeoForge pack puts two *files* under one id. The loser must come **off** the
classpath (first-URL-wins would otherwise let it shadow the winner and contribute its mixin configs).

- **Inventory** — `NestedCandidateInventory` walks every root and nested jar (depth ≤ 8, zip-bomb byte caps, no
  archive-count cap) into a graph of candidates and parent→child edges; nested jars are extracted, keyed by
  SHA-256, under `.forbric-kernel/candidates/`.
- **Selection** — `ReachableCandidateSelector` builds a whole-instance Boolean model (nested candidates exist only
  through selected parents) and solves it with SAT4J; `JointCandidateSelector` supplies the clauses: hard/soft
  dependencies, `breaks`/`incompatible` exclusions, and symbol contracts from `CandidateContractScanner` (only
  evidence strong enough to constrain selection — e.g. a mixin's required members). The search is bounded by
  work, never by a clock (`CONFLICT_BUDGET`, `VARIABLE_LIMIT`, `-Dforbric.arbitrationMaxNodes`, default 100 000),
  so the same folder selects the same jars on any machine.
- **Preferences** — top-level duplicates: `-Dforbric.dupeIdPreference`, falling back to `multiLoaderPreference`.
  Nested duplicates: `-Dforbric.nestedDupePreference`, default NeoForge, Fabric — chosen because a
  multi-loader library's per-loader builds stub out phases their loader lacks, and that order leaves the fewest
  callers talking to an empty method (the javadoc records the two cases that fixed it).
- **Overrides** — `-Dforbric.modOwner=sodium=fabric,…` or `<rundir>/forbric-mods.txt` (`<mod id> = <loader>`, one
  per line; the kernel writes a commented template the first time an instance has duplicates). The command line
  wins over the file.
- **Switched off** — `<rundir>/forbric-disabled.txt` lists jar file names in `mods/` (comments and bad lines as in
  `forbric-mods.txt`). `DisabledMods` keeps those jars out of the scan, so they never become claims, and they go
  into `Decision.suppressedJars` but never `rescueJars`; with `-Dforbric.crossJarArbitration=off` a decision
  holding only them is still cached. `load-report.txt` names them.
- **Residuals** — the losing ecosystem gets a presence-only alias so `isLoaded(id)` still answers
  (`Decision.aliases`); the other ecosystem's build of a mod that did load may lend a missing class as a last
  resort (`rescueJars`); `ArbitratedAwayClasses` measures what the losing build had that the winner lacks.
  `MergeReport` writes `.forbric-kernel/merge-report.txt` explaining each decision.
- `-Dforbric.crossJarArbitration=off` disables it entirely.

### 4.4 Answering "which loader am I on?" and "is X installed?"

- `LoaderProbePolicy` + `transform.LoaderProbeRewriter` (COREMOD phase) rewrite `Class.forName` call sites in a
  single-loader guest class so a platform probe (`FMLLoader`, `FabricLoader`) answers for the ecosystem that jar
  was arbitrated to (`-Dforbric.loaderProbes=off`).
- `net.forbric.api.ModPresence` is the cross-ecosystem answer. `transform.ForeignModPresenceInjector` ORs it into
  NeoForge's `ModList.isLoaded`; the Fabric side registers every NeoForge mod as a presence-only container
  (identity only — no entrypoints, mixins or assets), so `FabricLoader.isModLoaded` answers; and the NeoForge
  list is seeded with the Fabric mods (`-Dforbric.crossEcosystemPresence=off` restores single-loader answers).
  `net.forbric.api.ModIds` maps ids the ecosystems spell differently (`cloth-config` / `cloth_config`).
- `ModConstructionOrder` orders NeoForge construction topologically — a mod after everything it requires or
  declares `AFTER`; ties alphabetical; cycles named, not broken silently (`-Dforbric.modOrder=name` restores
  file-name order). Fabric mods initialise in Fabric Loader's own order, by mod id (`FabricLoadOrder`;
  `-Dforbric.fabricOrder=off` puts them back on the topological order).

## 5. The two ecosystems, driven by the kernel

### 5.1 Fabric — no Fabric Loader code runs

- `fabric.KernelFabricLoader` *is* the `FabricLoader` singleton: a view over kernel state, with its entrypoint
  index frozen before mod code runs. `KernelModContainer` exposes each jar through a zip `FileSystem`;
  `KernelLanguageAdapters` honours `languageAdapters` (fabric-language-kotlin); `KernelObjectShare`,
  `KernelVersion`, `KernelMappingResolver` (identity) complete the API.
- The mod-facing API is vendored under its own names: `src/main/java/net/fabricmc/api` (7 files) and
  `src/main/java/net/fabricmc/loader` (30 files: `loader.api.*` plus the narrow internals mods actually link against
  — `FabricLoaderImpl`, `ModContainerImpl`, `EntrypointStorage`, `Hooks`, `FabricLauncher`/`FabricLauncherBase`,
  `DefaultLanguageAdapter`, `StringUtil`, legacy `net.fabricmc.loader.FabricLoader`). `FabricLoaderInternals`
  pins those internals to the parent by exact name; `-Dforbric.fabricImpl=off` withholds them. The API level
  reported is `KernelFabricEcosystem.FABRIC_LOADER_API_LEVEL = "0.19.3"`.
- Entrypoints: `preLaunch` after Mixin (§3.2 step 19); `main` inside the registration window (server) or in
  `Minecraft.<init>` (client, default); `client` in `Minecraft.<init>`; `server` on the dedicated server. Each
  mod's entrypoint is invoked in isolation — one throwing mod is recorded and skipped — with a NeoForge
  `ModContainer` for that mod active (`KernelForeignShimContext`), because a Fabric mod may hold the NeoForge build
  of a multi-loader library.
- Fabric access wideners / class tweakers run in the `ACCESS` phase (`access.ClassTweakerTransformer`);
  `@Environment` stripping in `ENV_STRIP` (`EnvironmentStripTransformer`, only for classes from jars arbitrated to
  Fabric).

### 5.2 NeoForge

- **Identity** — `PassiveSeeder` creates a current `FMLLoader`, `FMLPaths`, a `LoadingModList` and `ModList` built
  from the jars this boot selected — no discovery, no module layer, no sorting.
- **Construction** — `KernelModLoader`: a `BusBuilder` `IEventBus` plus a kernel `ModContainer`
  (`net.forbric.kernel.runtime.KernelModContainer`); constructor arguments filled by type (`IEventBus`, `Dist`,
  `ModContainer`).
- **Buses** — mod-bus events the kernel posts itself go through `KernelLifecycle.postModBusEvent`; setup phases
  through `runtime.KernelNeoSetup`; deferred work runs on the thread NeoForge runs it on (`NeoDeferredWork`).
- **Enum extensions** — `NeoEnumExtensions` feeds every jar's `META-INF/enumextensions.json` to NeoForge's own
  `RuntimeEnumExtender`; `NeoEnumExtensionInjector` is registered near the end of COREMOD (its placement is
  load-bearing: it defines FML classes at that point), and only if some mod declares an extension.
- **Coremods** — NeoForge's coremod jar is never loaded; `transform.NativeCoremodParity` performs its rewrites
  (flower pot `potted`, biome climate/effects, structure settings, `finalizeSpawn`) *after* Mixin, where NeoForge
  runs them.

### 5.3 Cross-ecosystem services that are not events

- **Networking** — `interop.PayloadInterop` selects a custom-payload codec by runtime payload class where Fabric
  API and NeoForge share a vanilla channel id; `CommonNetworkInteropInjector` arbitrates the `c:version` /
  `c:register` channel both claim (`-Dforbric.commonNetworkInterop=off`); the kernel applies NeoForge's
  registry sync, and fabric-api's when fabric-api is installed, to the game registries, and
  `KernelRegistryRevert` restores pre-connection ids on disconnect; `NetworkChannelCensus` compares registered
  vs declared channels. `KernelClientSmoke`'s
  `-Dforbric.clientSmokeCarry=<tick>` drill drives the whole chain through the game's own key and mouse input.
- **Item/fluid/energy transfer** — `KernelTransferInterop` + `runtime/transfer/` bridge Fabric's transfer API
  and NeoForge's `ResourceHandler`, and Team Reborn Energy when installed
  (`-Dforbric.transferBridge=off`, `-Dforbric.hopperFabricStorage=off`). Active only when the relevant APIs are
  present, checked by resource.

## 6. The transform pipeline

`transform.TransformPhase` fixes the order: `RAW_PATCH`, `DEOBF_REMAP`, `ENV_STRIP`, `ACCESS`, `COREMOD`,
`FABRIC_BUILTIN`, `MIXIN`. `TransformChain` runs the chain phases (`RAW_PATCH` … `FABRIC_BUILTIN`); within a phase,
`predepends` topologically, then `sortIndex`, then registration order. `MIXIN` is terminal and cannot be registered
into the chain. On 26.2 nothing is registered in `RAW_PATCH` or `DEOBF_REMAP`.

What `KernelBoot` registers (91 call sites; some conditional):

| Phase | Registered |
| --- | --- |
| `ENV_STRIP` | `EnvironmentStripTransformer` |
| `ACCESS` | `ClassTweakerTransformer` (Fabric), `access.AccessTransformer` built from every mod jar's **and the carrier's** `META-INF/accesstransformer*.cfg` — the carrier's AT matters because the base's access must match what NeoForge's own runtime expects (`MenuScreens.register` came out private without it) |
| `COREMOD` | 86 registrations: the loader-probe rewriter, the Mixin-weaver slot (`FmlContextLoaderRewriter`, `ModuleClassLoaderInitInjector`), `GuestMixinPluginGuard`, the lifecycle redirect, `ForbricMergedBaseCompatTransformer`, and most of the 70 `*Injector` classes in `transform/`, each repairing one named seam the patched base broke or landing one bridge (§8) |
| `FABRIC_BUILTIN` | `RestoredAccessTransformer` (re-applies access to members COREMOD restored) |

The post-Mixin stage (`KernelMixinBootstrap`) is a fixed composition:

```
Mixin (via MixinWeaverSlot) → NativeCoremodParity → PostMixinFixups → InterfaceDefaultConflictRepair
```

Notable repairs by family (read each class's javadoc for the case that motivated it):

- **Base invariants** — `ForbricMergedBaseCompatTransformer` (lambda bootstrap handles vs. static-ness, key-mapping
  `MAP` initializer and vanilla's `KeyMapping.MAP` back as a view of
  the mappings by key (`KernelKeyMappingMap`; NeoForge re-types it, and LiquidBounce reads it on every key press in
  a screen), vanilla's `FriendlyByteBuf.writeByte(int)` called again where NeoForge's recompile bound a byte-typed call to
  its extension's `writeByte(byte)`, which only forwards there (14 sites in 10 network `write` methods; ViaFabricPlus'
  ability-flag redirect anchors on vanilla's call; `-Dforbric.vanillaWriteByte=off`), and retargeting the base's baked-in
  calls to `net/forbric/loader/impl/…` onto `net.forbric.kernel.interop`), `DuplicateLambdaPruneInjector`
  (orphaned lambdas a name-only mixin selector would bind to), `WidenedFieldTwinInjector` (vanilla-descriptor
  twins of re-typed fields), `MethodBodyNeuter`.
- **Packs and data** — `DataPackHookInjector`, `ClientPackHookInjector`, `PackMetadataFailSoftInjector`,
  `PackOverlayMutabilityInjector`, `NullPackGuardInjector`, `PackScreenHiddenFilterInjector`,
  `RegistryDirectoryOwnerInjector`, `RegistryAliasParityInjector` (§9).
- **UI** — `ModsButtonRedirector` (NeoForge's pause-menu lambda opens `KernelModListScreen`),
  `HudElementBridgeInjector`, `CreativePagerBridgeInjector`, `EarlyKeyMappingRegistrationInjector`.
- **Instrumentation** — `ClientSmokeTickInjector` (inert unless `-Dforbric.clientSmoke=true`),
  `ServerTickSamplerInjector`, `CompatibilityPromptTickInjector`,
  `ServerCompatibilityTickInjector`.
- **Hardening** — `ChunkExecutorGuardInjector` (refuses work offered to a stopped server's chunk executor;
  `-Dforbric.chunkExecutorGuard=off`).

Transformers that promise to land on a class declare it (`AnchorSet`); `AnchorLedger` reports a class that
passed through without being changed (a **Miss**, logged at ERROR immediately) and, at the census landmark,
classes never loaded. `RepairDriftCensus` (`run/compat/repair-drift.sh`) replays the claims against a *candidate*
game build — the question a carrier bump asks.

## 7. Mixin on the patched base

### 7.1 The kernel is the Mixin service

`mixin.ForbricMixinService` is registered through `META-INF/services/org.spongepowered.asm.service.IMixinService`
(with `ForbricMixinServiceBootstrap` and `ForbricGlobalPropertyService`); the Mixin library is Fabric's fork
(`net.fabricmc:sponge-mixin`, `mixin_version` in `gradle.properties`), and MixinExtras is the kernel-bundled
`mixinextras-fabric` (game-side, because its generated `LocalRef` classes must share the game's loader).
`KernelMixinBootstrap.init` binds the service, registers every config, installs `KernelMixinErrorHandler`, installs
the weaver as the last pipeline stage, then moves the environment to `INIT` and `DEFAULT`. Configs are prepared —
and plugins constructed — on the first class through the transformer, which is `KernelRuntimeClasses.verify`.

Order: Fabric configs in Fabric Loader's order, NeoForge configs appended. Mixin orders by priority;
registration order only breaks ties, so the least-proven set becomes the outer wrapper around a known-good stack.
`MixinWeaverSlot` lets a guest replace the weaver the way NeoForge allows (LibJF wraps
`FMLMixinClassProcessor.transformer`).

### 7.2 What happens to a guest config before Mixin reads it

`ForbricMixinService` rewrites each config's JSON:

1. **Whole-config gate** — `MixinConfigPolicy.isDisabled`: the built-in list in `MergedBaseMixinCompat`
   (`-Dforbric.mergedBaseCompat=off` drops it; `-Dforbric.enableMixinConfigs` forces one back),
   plus `-Dforbric.disableMixinConfigs` (csv, trailing `*` glob).
2. **Relaxation** — every config not starting with `forbric` is a guest config and is relaxed:
   `injectors.defaultRequire → 0`, `overwrites.requireAnnotations → false`, `required → false`. Per-injector
   `require`/`expect` still win. `-Dforbric.relaxGuestMixins=off` restores strict behaviour;
   `-Dforbric.relaxMixinOverwrites` relaxes named configs when that is off; `-Dforbric.mixinDiagnostics` keeps
   injection requirements strict (so every misfit is reported) while keeping `required=false`.
3. **Per-mixin drops** — the union of `KernelGuestMixinAdapter.unfitMixins` (below), the hand list
   `MergedBaseMixinCompat.SUPPRESSED_MIXINS`, and `-Dforbric.suppressMixins=config:Mixin,…`, minus
   `-Dforbric.keepMixins`. Dropping a mixin also drops every mixin that depends on an interface it contributed.

### 7.3 `MixinFit` — resolution, not provenance

`KernelGuestMixinAdapter` asks `MixinFit.evaluate` to resolve every anchor a guest mixin names — each `@Shadow`,
each injector's target method, each `@At(target=…)` — against the **post-chain** bytes of the target:

| Verdict | Meaning | Default action |
| --- | --- | --- |
| `FIT` | every anchor resolves | apply |
| `PARTIAL` | some resolve | **apply** (drop only under `-Dforbric.mixinFit=strict`); always reported |
| `UNFIT` | none resolve | drop |
| `HAZARD` | applies cleanly but shadows a field the base orphaned | drop |

Refinements: pure accessor/invoker mixins are always kept; anchors satisfied by another mod's mixin
(`ForeignMixinTargets`, `MixinAddedMembers`) count as resolved; `@Group` injectors are judged as a group; an
injector bound only to a merged-base method nothing in the merged game calls is **not** resolved (liveness,
`MergedBaseUncalledMethods`, `-Dforbric.mixinFit.liveness=off`) unless an installed mod calls it; and a name-only
`@Inject` selector is **not** resolved when the method it binds — Mixin takes the first of that name the target
declares — is not one the handler was written for (that method's arguments then the callback its return calls for, or
the callback alone), which Mixin would reject as "Invalid descriptor" (`-Dforbric.mixinFit.handlerFit=off`): the merged
base can put a carrier's overload where vanilla's method was, for a mod of any ecosystem. A selector `MixinOverloadPin`
will spell (§7.4) is judged on the overload it lands on, so the pin and this rule never both act on one injector; a
`@Surrogate` counts only as Mixin looks one up — the handler's name, exactly the bound method's callback descriptor, a
visible annotation. Where the name binds exactly one method, the handler captures no locals and one of its `@At`s is
sure to find a point in that method (`HEAD`; `RETURN`/`TAIL` where it returns; an `INVOKE`, `INVOKE_ASSIGN`, `FIELD` or
`NEW` whose member is there, past its `ordinal`; never with a slice), the miss is a *rejection* (`MixinFit.Rejection`):
Mixin checks the handler at each point it finds and throws at the first, whatever `require` says, and the exception
fails the mixin's application to that class — every injector after it, and the game in a config that stays required.
Where no point is sure, Mixin may find none, inject nothing and throw nothing, so the binding stays an ordinary miss
and its line says so (`-Dforbric.mixinFit.rejectionPoint=off` reads every such binding as a rejection, as before). A
mixin kept with a rejection — `PARTIAL`, kept for its misses on another mod's class, or `UNFIT` and kept because another
mod's mixin targets the class — is therefore never left as it is: the injector is taken out before Mixin reads the
mixin (`GuestInjectorPruner`, §7.4, asking the same rule of the node Mixin receives, with the target's code) when
nothing else in the mixin calls it, it is in no `@Group` and no target binds it as written — the rest of the mixin
applies, and the removal is a `CONFIRMED` finding, required when the author's own count (`require`, else
`defaultRequire`) is at least one — and otherwise the whole mixin is left out like an `UNFIT` one. A mixin a kernel
repair supersedes (`SupersededMixins`) is always left out whole, with a row that asks nothing and resolves once the
repair is seen. A mixin kept by name (`MergedBaseMixinCompat.KEPT_MIXINS`, `-Dforbric.keepMixins`) is handed to Mixin
unjudged. `-Dforbric.guestInjectorPruner.refused=off` keeps such a mixin in front of Mixin as before, and the `PARTIAL`
summary then counts it apart. A `PARTIAL` or `UNFIT` mixin is first offered to `MixinRetarget`, and its plan is taken when the
rewritten mixin misses fewer anchors, is one the adapter keeps, and adds no rejection — an `UNFIT` one's rewrite may
keep none (`MixinRetarget.adopt`). A mixin the kernel leaves out by name is not judged at all, so it gets no verdict line
and no place in the `PARTIAL` count. `MixinFitReport` runs the same judgement offline:
`MixinFitReport <merged-base.jar> <mods-dir> [--verbose]`.

A miss is the merge's only if the mod's own platform had the member: vanilla 26.2 for a Fabric mod, MinecraftForge's
or NeoForge's patched 26.2 for theirs (the config owner's ecosystem, `MixinConfigOwners.ecosystemOf`; a config no
single mod claims is not asked). Those patched games declare methods vanilla does not — `KeyMapping.getKeyModifier()`,
`AxeItem.canPerformAction`, NeoForge's `EnderDragon.getParts()` — and the merge dropped or retyped some of them, so
"vanilla lacks it too" proves nothing for a MinecraftForge or NeoForge mod. An injector whose `method` selectors are
all plain names of methods that platform lacks too, and which nothing requires to inject — no `require` ≥ 1, the
config's original `injectors.defaultRequire` 0, no `@Group`, no `mixin.debug.countInjections` — is one native Mixin
skips without a word while the rest of the mixin applies (sponge-mixin 0.17.x and upstream Mixin 0.8.7 alike:
`TargetSelectors.validate` throws only for a required count; the config's `required` flag only decides whether such an
error is fatal). "Plain" means a name, optionally a descriptor, optionally the target itself as owner: a `@`-dynamic
selector (MixinSquared's `@MixinSquared:Handler` resolves to another mixin's handler), a `+` or `{n,}` quantifier (its
minimum throws whatever `require` says), a dotted owner, a regex and a malformed descriptor are never answered.
`NativeAbsentTargets` counts such an injector neither resolved nor missing and logs it on one info line, so the mixin
is judged on the rest and, with nothing else missing, goes to Mixin whole — no finding, no policy stop. Not Enough
Crashes' `@Inject` into `BlockEntity.populateCrashReport` (26.2 calls it `fillCrashReportCategory`) is the case.
"The platform lacks it" is answered from the merged base's raw bytes plus the shipped difference
`native-only-methods.txt`: per platform, the methods its game declares in vanilla's packages that the merged base does
not (565 for vanilla, 424 for MinecraftForge, 14 for NeoForge) and the classes there only the merged base has (18, 10
and 6). A method the raw class still declares, a class outside `net/minecraft/`/`com/mojang/`, and another mod's class
are never called absent. The rows hold only for the base they were derived from, so the table also records that base's
