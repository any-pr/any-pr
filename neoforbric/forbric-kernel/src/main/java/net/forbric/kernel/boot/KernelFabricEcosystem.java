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
