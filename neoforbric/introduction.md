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

