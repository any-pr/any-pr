/*
 * Copyright 2026 The Forbric Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.forbric.kernel.boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;

import net.forbric.api.Side;
import net.forbric.api.DiscoveredMod;
import net.forbric.api.Ecosystem;
import net.forbric.api.ModPresence;
import net.forbric.kernel.fabric.FabricModDiscovery;
import net.forbric.kernel.mixin.MixinConfigOwners;
import net.forbric.kernel.fabric.KernelFabricLoader;
import net.forbric.kernel.fabric.KernelModContainer;
import net.forbric.kernel.fabric.KernelCustomValue;
import net.forbric.kernel.fabric.KernelModMetadata;
import net.forbric.kernel.mixin.MergedBaseMixinCompat;
import net.forbric.kernel.mixin.MixinConfigPolicy;
import net.forbric.kernel.util.ForbricLog;
import net.forbric.api.UnifiedDependency;
import net.forbric.kernel.fabric.KernelMetadataSupport;
import net.forbric.api.ModCatalog;

/**
 * Drives the Fabric ecosystem natively: discovery &rarr; {@link KernelFabricLoader} &rarr; entrypoints.
 *
 * <p>No Fabric Loader code runs. The kernel discovers {@code fabric.mod.json}s itself, builds the mod-facing
 * {@code FabricLoader} view, and invokes {@code ModInitializer.onInitialize()} at the point the registries are
 * writable — the same native registration window in which {@link KernelLifecycle} fires the Forge families'
 * {@code RegisterEvent}s. A Fabric mod's {@code onInitialize} calls {@code Registry.register(...)} directly, so
 * it must run inside that unfreeze/freeze span; running it before {@code Bootstrap.bootStrap} would touch
 * registries that do not exist yet, and after the freeze would throw.
 *
 * <p>The {@code main} entrypoints are guarded to run at most once per process, mirroring the old weld's shared
 * {@code ForbricFabricMains} guard: once the tri-in-one client/server paths both open registration windows, only
 * the first may run them.
 */
public final class KernelFabricEcosystem {
	/**
	 * The Fabric Loader API level the kernel implements. Vendored from the fabric-loader 0.19.3 API sources; the
	 * shipped ecosystem requires {@code >=0.18.4}. This is NOT a claim that Fabric Loader is present — the only
	 * {@code net.fabricmc.loader.impl} classes in this process are the slivers mods link against (see
	 * {@code FabricLoaderInternals}), in front of the kernel's own loader.
	 */
	public static final String FABRIC_LOADER_API_LEVEL = "0.19.3";

	private static final AtomicBoolean MAINS_RAN = new AtomicBoolean();
	private static final AtomicBoolean CLIENTS_RAN = new AtomicBoolean();
	/** The entrypoint keys whose phase has run, for {@link KernelFabricLoader#adoptFabricStorage}. */
	private static final Set<String> PHASES_RAN = ConcurrentHashMap.newKeySet();

	/**
	 * Forgets which phases ran, so one test's {@code preLaunch} or {@code main} does not follow the next one around the
	 * JVM as "already ran". Tests only; a real process runs each phase once.
	 */
	public static void resetPhasesForTests() {
		MAINS_RAN.set(false);
		CLIENTS_RAN.set(false);
		PHASES_RAN.clear();
	}

	private static volatile KernelFabricLoader loader;

	private KernelFabricEcosystem() {
	}

	/** Whether any Fabric mod was discovered (so the rest of the kernel can skip Fabric work entirely). */
	public static boolean active() {
		return loader != null && !loader.getAllMods().isEmpty();
	}

	/**
	 * Discovers Fabric mods under {@code modsDir}, creates the process-wide {@code FabricLoader}, and returns the
	 * jars (mods + their extracted JiJ children) that must join the game class loader.
	 */
	public static List<Path> discover(EnvType envType, Path gameDir, String gameVersion, String[] launchArgs) {
		return discover(envType, gameDir, gameVersion, launchArgs, DuplicateModArbiter.Decision.none());
	}

	/**
	 * @param dupes cross-jar arbitration result; a Fabric jar another jar's copy of the same mod won is skipped
	 *              ENTIRELY — not merely left unregistered, as a universal jar is. It must not reach the classpath
	 *              either, or its classes still shadow the winner's (first-URL-wins) and its mixin configs still
	 *              apply.
	 */
	public static List<Path> discover(EnvType envType, Path gameDir, String gameVersion, String[] launchArgs,
			DuplicateModArbiter.Decision dupes) {
		return build(scan(envType, gameDir, dupes, gameVersion), envType, gameDir, gameVersion, launchArgs, dupes);
	}

	/**
	 * The FIRST half: walk {@code mods/}, extract the JiJ children, build the containers — and register nothing.
	 *
	 * <p>Split from {@link #build} so the nested jars of BOTH families are known before either family loads one.
	 * Cross-ecosystem arbitration cannot happen any earlier: the Forge family's nested jars come out of
	 * {@code KernelBoot.extractForgeFamilyJarJar} and Fabric's come out of here, so until both have run, "two jars
	 * claim this id" is a question with only half its evidence. Registering here as well would settle the question
	 * by whichever walk happened to run first, which is the ordering accident this split exists to remove.
	 */
	public static FabricModDiscovery scan(EnvType envType, Path gameDir, DuplicateModArbiter.Decision dupes) {
		return scan(envType, gameDir, dupes, null);
	}

	/** {@code gameVersion}: what a nested mod's {@code minecraft} requirement is held against ({@code null}: not judged). */
	public static FabricModDiscovery scan(EnvType envType, Path gameDir, DuplicateModArbiter.Decision dupes,
			String gameVersion) {
		return scan(envType, gameDir, dupes, gameVersion, List.of());
	}

	/**
	 * As above; {@code requiredElsewhere} is what mods this discovery does not read hard-require, for the no-plan
	 * walk (see {@link FabricModDiscovery#setRequiredElsewhere}).
	 */
	public static FabricModDiscovery scan(EnvType envType, Path gameDir, DuplicateModArbiter.Decision dupes,
			String gameVersion, List<net.forbric.kernel.fabric.NestedFabricRequirements.Requirement> requiredElsewhere) {
		Path cacheDir = gameDir.resolve(".forbric-kernel").resolve("jij");
		FabricModDiscovery discovery = new FabricModDiscovery(envType, cacheDir);
		discovery.setSkip(dupes::suppressed);
		discovery.setPlatform(net.forbric.kernel.fabric.NestedFabricRequirements.Platform.running(gameVersion));
		discovery.setRequiredElsewhere(requiredElsewhere);
		discovery.discover(gameDir.resolve("mods"));
		return discovery;
	}

	/**
	 * The SECOND half: build the loader and register what {@code dupes} did not suppress.
	 *
	 * @param dupes the FINAL decision, i.e. after {@code DuplicateModArbiter.arbitrateNested} — a nested jar that
	 *              lost must not be registered AND must not reach the classpath, or its classes still shadow the
	 *              winner's and its mixin configs still apply. That is the whole defect for a mod like Xaero's
	 *              xaerolib: its losing half's mixins stayed live and called into the half-built winner.
	 */
	public static List<Path> build(FabricModDiscovery discovery, EnvType envType, Path gameDir, String gameVersion,
			String[] launchArgs, DuplicateModArbiter.Decision dupes) {
		return build(discovery, envType, gameDir, gameVersion, launchArgs, dupes, null);
	}

	public static List<Path> build(FabricModDiscovery discovery, EnvType envType, Path gameDir, String gameVersion,
			String[] launchArgs, DuplicateModArbiter.Decision dupes, Path gameJar) {
		KernelFabricLoader fabric = KernelFabricLoader.create(envType, gameDir, gameDir.resolve("config"),
				launchArgs, gameVersion);

		fabric.register(new KernelModContainer(
				KernelModMetadata.builtin("minecraft", gameVersion, "Minecraft"), gameJar, null));
		fabric.register(new KernelModContainer(
				KernelModMetadata.builtin("java", String.valueOf(Runtime.version().feature()), "Java"), null, null));
		fabric.register(new KernelModContainer(
				KernelModMetadata.builtin("fabricloader", FABRIC_LOADER_API_LEVEL, "Fabric Loader (Forbric kernel)"),
				null, null));
		int builtins = fabric.getAllMods().size();

		// A universal jar also ships a fabric.mod.json; register it as a Fabric mod only if Fabric OWNS the jar,
		// otherwise the same mod runs its Fabric entrypoints on top of the Forge/NeoForge @Mod that already claimed
		// it (see MultiLoaderArbiter). The jar stays on the classpath either way — the owning family needs its
		// classes; only the Fabric-side registration (and with it the entrypoints) is skipped.
		int suppressed = 0;
		int lostNested = 0;
		// Registered in DEPENDENCY order, and put in Fabric Loader's order (by mod id) just before the freeze; see
		// putInFabricOrder. The dependency order still decides which of two same-id containers is kept, as it
		// always did, and it is the order the Forge-family seeders read the Fabric mods in below.
		for (KernelModContainer container : orderByDependency(discovery.getContainers())) {
			Path jar = container.getJar();
			if (jar != null && MultiLoaderArbiter.suppressedFor(jar, Ecosystem.FABRIC)) {
				suppressed++;
				continue;
			}
			// Cross-jar arbitration, which by now has seen the nested jars too. Unlike the universal-jar case
			// above, this jar does not stay on the classpath (see build's @param dupes) — the winner's copy is
			// the same library, and leaving the loser's would keep its platform mixins live.
			if (jar != null && dupes.suppressed(jar)) {
				lostNested++;
				continue;
			}
			fabric.register(container);
		}
		// Everything registered so far is what Fabric Loader itself would list: the builtins and the Fabric mods.
		int fabricOwn = fabric.getAllMods().size();
		if (lostNested > 0) {
			ForbricLog.info("[Forbric/Fabric] skipped %d Fabric registration(s) whose mod id another jar won",
					lostNested);
		}
		if (suppressed > 0) {
			ForbricLog.info("[Forbric/Fabric] skipped %d Fabric registration(s) for jars a Forge family owns", suppressed);
		}

		// Presence aliases: a mod whose Fabric jar lost cross-jar arbitration is still HERE — the winner's jar is
		// 98–100% the same classes — but without this, FabricLoader.isModLoaded(id) answers false and a Fabric mod
		// that gates an integration on that check silently disables it. Register the identity, nothing else: no
		// entrypoints, no mixins, no assets, all of which the winner already provides.
		for (DuplicateModArbiter.Alias alias : dupes.aliasesFor(Ecosystem.FABRIC)) {
			fabric.register(KernelModContainer.presence(KernelModMetadata.builtin(alias.modId(), alias.version(),
					alias.modId(), foreignCustomValues(alias.modId())), presenceSource(alias.modId(), dupes)));
			ForbricLog.info("[Forbric/Fabric] presence alias '%s' %s — its Fabric jar lost arbitration, but the "
					+ "winning jar supplies the classes; isModLoaded now answers", alias.modId(), alias.version());
		}

		// The same identity problem across ECOSYSTEMS. A Fabric mod asking isModLoaded("jei") next to a NeoForge
		// JEI was told no, because each loader only ever knew its own family's mods; the answer is almost always a
		// compatibility branch, so a wrong no silently disables an integration that would have worked. Presence
		// only, exactly like the arbitration aliases above: identity, no entrypoints, no mixins, no assets — the
		// mod is really loaded, by the other family's lifecycle, which owns everything else about it.
		int foreign = 0;
		for (DiscoveredMod mod : ModPresence.forgeFamilyMods()) {
			if (mod.getId() == null || mod.getId().isBlank()) continue;
			if (fabric.getModContainer(mod.getId()).isPresent()) continue;
			fabric.register(KernelModContainer.presence(KernelModMetadata.builtin(mod.getId(),
					mod.getVersion() == null ? "0" : mod.getVersion(),
					mod.getDisplayName() == null ? mod.getId() : mod.getDisplayName(),
					customValuesOf(mod)), loadedFrom(mod)));
			foreign++;
		}
		if (foreign > 0) {
			ForbricLog.info("[Forbric/Fabric] %d Forge-family mod(s) registered for presence only — a Fabric mod "
					+ "asking isModLoaded() about one of them now gets the truth instead of no", foreign);
		}

		// Read before the reorder: this is the order the Forge-family seeders below have always been given.
		List<ModContainer> registered = List.copyOf(fabric.getAllMods());
		putInFabricOrder(fabric, registered, builtins, fabricOwn);

		fabric.freeze();
		loader = fabric;

		// The mirror image: what the Forge-family lists have to be seeded with so their mods can see these.
		// Built-ins and the jar-less aliases above are left out — NeoForge's list is built per JAR, and those
		// have no jar; "minecraft"/"java"/"fabricloader" are not mods a compatibility branch asks about anyway.
		//
		// In registration order, not getAllMods() order. KernelModLoader orders the Forge-family @Mod classes over
		// a graph that includes these (a NeoForge mod can require a Fabric one), and where a Fabric mod sits in that
		// graph's input decides where a Forge-family mod waiting on it lands. Handing it the Fabric order would
		// reorder NeoForge and MinecraftForge mods as a side effect.
		List<DiscoveredMod> fabricMods = new ArrayList<>();
		for (ModContainer container : registered) {
			if (!(container instanceof KernelModContainer kernel) || kernel.getJar() == null) continue;
			String id = kernel.getMetadata().getId();
			// Minecraft has real resource roots, but remains a builtin rather than a foreign mod alias.
			if (id != null && java.util.Set.of("minecraft", "java", "fabricloader").contains(id)) continue;
			if (id == null || id.isBlank() || ModPresence.isLoaded(id)) continue;
			// The provides aliases ride along. FabricLoader resolves them itself, but the Forge-family lists and
			// ModPresence are built from THIS list, and every LibJF module is named through an alias.
			fabricMods.add(new DiscoveredMod(Ecosystem.FABRIC, id,
					String.valueOf(kernel.getMetadata().getVersion()), kernel.getMetadata().getName(),
					unifiedDependencies(kernel.getMetadata()), List.of(), null, kernel.getJar().toString())
					.withAliases(List.copyOf(kernel.getMetadata().getProvides())));
		}
		ModPresence.publishFabric(fabricMods);

		List<Path> jars = new ArrayList<>();
		for (Path jar : discovery.getClasspathJars()) {
			if (!dupes.suppressed(jar)) jars.add(jar);
		}
		ForbricLog.info("[Forbric/Fabric] discovered %d Fabric mod(s) in %d jar(s) (incl. nested)",
				discovery.getContainers().size(), jars.size());

		return jars;
	}

	/** Points entrypoint resolution at the transforming loader. Must precede any entrypoint invocation. */
	public static void bindGameLoader(ClassLoader gameLoader) {
		if (loader != null) loader.setGameLoader(gameLoader);
	}

	/**
	 * Every discovered Fabric mod's declared access widener ({@code .classtweaker}) file contents, in mod order.
	 *
	 * <p>Read from each mod's own jar. A declared-but-missing file is a mod packaging error: warn and skip rather
	 * than fail the launch, since the mixins that need it will fail loudly on their own.
	 */
	public static List<byte[]> accessWideners() {
		List<byte[]> files = new ArrayList<>();
		for (net.forbric.kernel.access.ClassTweakerTransformer.File file : accessWidenerFiles()) files.add(file.bytes());
		return files;
	}

	/** {@link #accessWideners()} with each file's jar name beside it, for the access census. */
	public static List<net.forbric.kernel.access.ClassTweakerTransformer.File> accessWidenerFiles() {
		if (loader == null) return List.of();

		List<net.forbric.kernel.access.ClassTweakerTransformer.File> files = new ArrayList<>();

		for (ModContainer mod : loader.getAllMods()) {
			if (!(mod instanceof KernelModContainer)) continue;

			KernelModContainer container = (KernelModContainer) mod;
			String path = container.getMetadata().getAccessWidener();
			if (path == null || path.isEmpty()) continue;

			try (java.util.jar.JarFile jar = new java.util.jar.JarFile(container.getJar().toFile())) {
				java.util.zip.ZipEntry entry = jar.getEntry(path);

				if (entry == null) {
					ForbricLog.warn("[Forbric/Access] %s declares accessWidener '%s' which is not in its jar",
							container.getMetadata().getId(), path);
					continue;
				}

				try (java.io.InputStream in = jar.getInputStream(entry)) {
					files.add(new net.forbric.kernel.access.ClassTweakerTransformer.File(
							container.getJar().getFileName().toString(), in.readAllBytes()));
				}
			} catch (Exception e) {
				ForbricLog.warn("[Forbric/Access] could not read accessWidener of %s: %s",
						container.getMetadata().getId(), String.valueOf(e));
			}
		}

		return files;
	}

	/**
	 * Every discovered Fabric mod's mixin configs that apply to the running side, in mod order.
	 *
	 * <p>A config declared {@code {"config": "...", "environment": "client"}} is dropped on a dedicated server —
	 * its mixins target client-only classes that do not exist here, and registering it would fail the whole config.
	 */
	public static List<MixinConfigOwners.Owned> mixinConfigs() {
		if (loader == null) return List.of();

		EnvType envType = loader.getEnvironmentType();
		List<MixinConfigOwners.Owned> configs = new ArrayList<>();

		for (ModContainer mod : loader.getAllMods()) {
			if (!(mod instanceof KernelModContainer)) continue;

			for (KernelModMetadata.MixinConfigDecl decl : ((KernelModContainer) mod).getMetadata().getMixinConfigs()) {
				if (!decl.environment().matches(envType)) continue;

				if (MixinConfigPolicy.isDisabled(decl.config())) {
					ForbricLog.warn("[Forbric/Mixin] mixin config %s DISABLED by -Dforbric.disableMixinConfigs — "
							+ "that module's mixins will not apply",
							MixinConfigOwners.describe(decl.config()));
					continue;
				}

				configs.add(new MixinConfigOwners.Owned(decl.config(), mod.getMetadata().getId(),
						Ecosystem.FABRIC));
			}
		}

		return configs;
	}

	/** Publishes the game object (the {@code MinecraftServer}) for {@code FabricLoader.getGameInstance()}. */
	public static void setGameInstance(Object gameInstance) {
		if (loader != null) loader.setGameInstance(gameInstance);
	}

	/**
	 * {@code -Dforbric.preLaunchFailure=warn}: a {@code preLaunch} entrypoint that throws, or cannot be loaded, is only
	 * logged again, and its mod still reads as loaded.
	 */
	public static final String PRELAUNCH_FAILURE_PROPERTY = "forbric.preLaunchFailure";

	/**
	 * Runs the {@code preLaunch} entrypoints, before any game class is loaded. Per Fabric's contract these must
	 * not touch game classes; the kernel does not enforce that, but it does run them at the correct point.
	 *
	 * <p>A {@code preLaunch} that throws fails its mod, as {@code main}, {@code client} and {@code server} already did:
	 * on Fabric it takes the whole game down. It used to be an ERROR line and nothing else, so Core Lib's preLaunch
	 * dying on a missing Fabric Loader internal left every SuperMartijn642 mod without its content while the Mods
	 * screen called Core Lib loaded. An entrypoint that cannot even be loaded arrives here too: the loader hands a
	 * lifecycle key's load failure to its driver.
	 */
	public static void runPreLaunch() {
		if (loader == null) return;

		if (loader.hasEntrypoints("preLaunch")) {
			for (EntrypointContainer<PreLaunchEntrypoint> c
					: loader.getEntrypointContainers("preLaunch", PreLaunchEntrypoint.class)) {
				String id = c.getProvider().getMetadata().getId();

				try {
					c.getEntrypoint().onPreLaunch();
					ForbricLog.info("[Forbric/Fabric] preLaunch entrypoint of %s", id);
				} catch (Throwable t) {
					ForbricLog.error("[Forbric/Fabric] preLaunch entrypoint of " + id + " failed", t);
					if (!"warn".equalsIgnoreCase(System.getProperty(PRELAUNCH_FAILURE_PROPERTY, "fail"))) {
						ModCatalog.mark(id, ModCatalog.Status.FAILED, "its preLaunch entrypoint threw");
					}
				}
			}
		}
		PHASES_RAN.add("preLaunch");
		// preLaunch is where Fabric mods edit Fabric Loader's own entrypoint index (Core Lib appends the entrypoint
		// that flushes every SuperMartijn642 mod's registrations), and a mixin plugin may already have: take it back
		// now, before the first phase that could run what was added.
		adoptFabricStorage();
	}

	/**
	 * Reads back Fabric Loader's internal entrypoint storage, if a mod has reached for it. Idempotent — an unchanged
	 * storage changes nothing — and so called again before each later phase, in case one was edited in between.
	 */
	private static void adoptFabricStorage() {
		KernelFabricLoader current = loader;
		if (current == null) return;
		try {
			current.adoptFabricStorage(PHASES_RAN);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Fabric] could not read back Fabric Loader's internal entrypoint storage — "
					+ "entrypoints a mod added there will not run", t);
		}
	}

	/**
	 * Runs every Fabric {@code main} entrypoint, plus the {@code server} one on a dedicated server, exactly once per
	 * process. Must be called with the registries unfrozen — this is where mods register content.
	 *
	 * <p>On a CLIENT this is called from {@code KernelLifecycle.onClientEntrypoints}, inside {@code Minecraft.<init>}
	 * and immediately before {@link #runClientEntrypoints()} — Fabric's own {@code Hooks.startClient} order, at
	 * Fabric's own point in the constructor. {@code Minecraft.getInstance()} is live there and {@code Options} is
	 * not yet built, which is the window mods are written against from both ends: keymapping registration reads
	 * {@code Minecraft.getInstance().options} and NPEs if the instance is null (run too early) or throws
	 * "GameOptions has already been initialised" (run too late). That hook reopens the registries and rebuilds what
	 * reopening invalidates, so "unfrozen" still holds — see {@code ClientEntrypointHookInjector}.
	 *
	 * <p>A dedicated server calls it from the pre-{@code Minecraft} registration window instead, because there is no
	 * {@code Minecraft} to wait for and Fabric's {@code startServer} runs {@code main} just as early.
	 *
	 * @return true if this call ran them, false if they had already run
	 */
	public static boolean runMainEntrypoints() {
		if (loader == null) return false;
		adoptFabricStorage();
		if (!MAINS_RAN.compareAndSet(false, true)) return false;

		EnvType envType = loader.getEnvironmentType();
		PHASES_RAN.add("main");
		int main = invoke("main", ModInitializer.class, ModInitializer::onInitialize);

		if (envType == EnvType.CLIENT) {
			ForbricLog.info("[Forbric/Fabric] invoked %d Fabric main entrypoint(s) in the %s window", main,
					mainsRunInConstructor() ? "Minecraft.<init>" : "pre-Minecraft registration");
		} else {
			// Fabric's startServer reads the storage afresh for each phase, so a 'server' entry a mod adds during its
			// own onInitialize runs. Read it back here too, and only then call the phase run; marking 'server' as run
			// before main dropped such an entry with a warning that it came too late.
			adoptFabricStorage();
			PHASES_RAN.add("server");
			int server = invoke("server", DedicatedServerModInitializer.class,
					DedicatedServerModInitializer::onInitializeServer);
			ForbricLog.info("[Forbric/Fabric] invoked %d Fabric main entrypoint(s) + %d server entrypoint(s)",
					main, server);
		}
		return true;
	}

	/**
	 * The containers in dependency order: the order they are registered in, which decides which of two same-id
	 * containers is kept, and the order the Forge-family seeders are given the Fabric mods in.
	 *
	 * <p>It is the order they INITIALISE in only under {@code -Dforbric.fabricOrder=off}; by default
	 * {@link #putInFabricOrder} puts them in Fabric Loader's order before anything runs. It was introduced to
	 * replace discovery order (jar file name, alphabetically), which made a mod whose file sorted before a library it
	 * requires initialise first. Fabric Loader has no such rule, and Fabric mods are written against the one it
	 * does have (see {@link FabricLoadOrder}), so the switch is where this order now lives.
	 *
	 * <p>Best effort: an order is an improvement, never a precondition, and losing it must not cost the pack its
	 * mods.
	 */
	private static List<KernelModContainer> orderByDependency(List<KernelModContainer> containers) {
		try {
			List<DiscoveredMod> known = new ArrayList<>(containers.size());
			for (KernelModContainer container : containers) {
				String id = container.getMetadata().getId();
				if (id == null || id.isBlank()) continue;
				known.add(new DiscoveredMod(Ecosystem.FABRIC, id,
						String.valueOf(container.getMetadata().getVersion()), container.getMetadata().getName(),
						unifiedDependencies(container.getMetadata()), List.of(), null, id)
						.withAliases(List.copyOf(container.getMetadata().getProvides())));
			}
			if (known.isEmpty()) return containers;

			List<KernelModContainer> sorted = ModConstructionOrder.sort(containers,
					c -> c.getMetadata().getId(), ModConstructionOrder.of(known));
			// Said only when it is also the order they initialise in. By default it is not, and this line would
			// claim the opposite of what happens.
			if (!sorted.equals(containers) && !FabricLoadOrder.enabled()) {
				ForbricLog.info("[Forbric/Order] %d Fabric mod(s) initialise in dependency order, not jar-file "
						+ "order (-Dforbric.modOrder=name to go back)", sorted.size());
			}
			return sorted;
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Order] could not order Fabric mods by dependency; using discovery order",
					t);
			return containers;
		}
	}

	/**
	 * Puts the Fabric mods in Fabric Loader's order — by mod id — before any of them runs.
	 *
	 * <p>Fabric Loader lists its mods, fills every entrypoint key and registers mixin configs in the order its
	 * resolver returns, which is the resolved set sorted by id, nested mods and builtins included (see
	 * {@link FabricLoadOrder}). Pets Mod is the case that showed it matters: its client JOIN listener throws in every
	 * singleplayer world, fabric-api's JOIN invoker does not catch per listener, and so every listener registered
	 * after it is skipped. Natively that spares bclib and OptiGUI, whose ids sort first; in dependency order both
	 * came after Pets Mod and lost their join handlers.
	 *
	 * <p>The first {@code fabricOwn} entries of {@code registered} — the builtins and the Fabric mods — are sorted.
	 * The presence-only identities after them have no Fabric Loader counterpart and keep their place at the end.
	 *
	 * <p>Best effort, like the dependency order it replaces: a failure keeps the registration order and says so.
	 *
	 * @param builtins how many of {@code registered} are the builtins, which are not counted as mods in the log line
	 */
	private static void putInFabricOrder(KernelFabricLoader fabric, List<ModContainer> registered, int builtins,
			int fabricOwn) {
		if (!FabricLoadOrder.enabled()) return;
		try {
			List<ModContainer> order = new ArrayList<>(FabricLoadOrder.byModId(registered.subList(0, fabricOwn),
					container -> container.getMetadata().getId()));
			order.addAll(registered.subList(fabricOwn, registered.size()));
			fabric.reorder(order);
			if (fabricOwn > builtins) {
				ForbricLog.info("[Forbric/Order] %d Fabric mod(s) initialise in Fabric Loader's order, by mod id, as "
						+ "native Fabric orders them (-D%s=off for Forbric's previous order)", fabricOwn - builtins,
						FabricLoadOrder.SWITCH);
			}
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Order] could not put Fabric mods in Fabric Loader's order; they initialise in "
					+ "the order they were registered in", t);
		}
	}

	/**
	 * A Fabric mod's declared dependencies in the unified model.
	 *
	 * <p>These used to be {@code List.of()} — not because Fabric mods declare none, but because nobody filled them
	 * in. A {@link DiscoveredMod} reporting an empty dependency list is indistinguishable from one that genuinely
	 * has none, so {@code DependencyAudit} silently judged only the Forge families and nothing said so.
	 *
	 * <p>Only POSITIVE kinds cross over. A {@code breaks}/{@code conflicts} entry is a requirement that a mod be
	 * ABSENT, and carrying it here as a dependency would make the audit report "requires X — not installed" about
	 * a mod that must not be installed. {@link UnifiedDependency} has no negative sense yet, and inventing one
	 * that nothing evaluates is how this field came to lie in the first place.
	 *
	 * <p>Fabric declares no side or ordering axis, so both stay neutral rather than being guessed at from the
	 * mod's {@code environment} — that says where the MOD runs, not where its requirement applies.
	 */
	static List<UnifiedDependency> unifiedDependencies(ModMetadata metadata) {
		List<UnifiedDependency> out = new ArrayList<>();
		for (ModDependency dep : metadata.getDependencies()) {
			if (!dep.getKind().isPositive()) continue;
			out.add(new UnifiedDependency(dep.getModId(), constraintOf(dep), !dep.getKind().isSoft()));
		}
		return out;
	}

	/** The predicate as declared; the array form is OR-joined, which is what {@code VersionPredicate} reads. */
	static String constraintOf(ModDependency dep) {
		if (dep instanceof KernelMetadataSupport.SimpleModDependency simple && !simple.getConstraints().isEmpty()) {
			return String.join(" || ", simple.getConstraints());
		}
		return "*";
	}

	/**
	 * The physical side this boot is running on, or {@code null} while the Fabric side has not been brought up.
	 *
	 * <p>There is no other authority for this in the kernel: the merged base carries the client classes even on a
	 * dedicated server, so "can I load Minecraft.class" answers the wrong question. Callers that would have to
	 * guess should treat {@code null} as "do not judge side-scoped things" rather than picking a side.
	 */
	public static Side physicalSide() {
		KernelFabricLoader current = loader;
