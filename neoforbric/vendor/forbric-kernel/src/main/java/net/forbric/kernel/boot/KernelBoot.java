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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.EnvType;

import net.forbric.api.DiscoveredMod;
import net.forbric.api.Ecosystem;
import net.forbric.api.ForeignType;
import net.forbric.api.ModPresence;
import net.forbric.kernel.access.ClassTweakerTransformer;
import net.forbric.kernel.classloading.ForbricClassLoader;
import net.forbric.kernel.classloading.LoaderProbePolicy;
import net.forbric.kernel.fabric.FabricModDiscovery;
import net.forbric.kernel.discovery.ForbricModDiscoverer;
import net.forbric.kernel.metadata.forge.EcosystemVersions;
import net.forbric.kernel.mixin.KernelMixinBootstrap;
import net.forbric.kernel.mixin.MixinConfigOwners;
import net.forbric.kernel.transform.ChunkExecutorGuardInjector;
import net.forbric.kernel.transform.ClientPackHookInjector;
import net.forbric.kernel.transform.ClientSmokeTickInjector;
import net.forbric.kernel.transform.CommonNetworkInteropInjector;
import net.forbric.kernel.transform.SodiumConfigUserBridgeInjector;
import net.forbric.kernel.transform.DataPackHookInjector;
import net.forbric.kernel.transform.DuplicateLambdaPruneInjector;
import net.forbric.kernel.transform.ForbricMergedBaseCompatTransformer;
import net.forbric.kernel.transform.ForeignModPresenceInjector;
import net.forbric.kernel.transform.GuestMixinPluginGuard;
import net.forbric.kernel.transform.HudElementBridgeInjector;
import net.forbric.kernel.transform.LifecycleHookInjector;
import net.forbric.kernel.transform.MergedBaseFrameRecomputer;
import net.forbric.kernel.transform.PortingLayerAbiInjector;
import net.forbric.kernel.transform.LoaderProbeRewriter;
import net.forbric.kernel.transform.MethodBodyNeuter;
import net.forbric.kernel.transform.NeoEnumExtensionInjector;
import net.forbric.kernel.transform.NullPackGuardInjector;
import net.forbric.kernel.transform.PackMetadataFailSoftInjector;
import net.forbric.kernel.transform.PackOverlayMutabilityInjector;
import net.forbric.kernel.transform.TransformChain;
import net.forbric.kernel.transform.TransformContext;
import net.forbric.kernel.transform.TransformPhase;
import net.forbric.kernel.util.ForbricLog;

/**
 * The shared boot flow behind {@link KernelServerLaunch} and {@code KernelClientLaunch}: build the one sovereign
 * {@link ForbricClassLoader} over the merged base + ecosystem carriers + all mods + MC libraries, install the
 * transform pipeline (access wideners → lifecycle redirect → concessions → Mixin), discover all three ecosystems,
 * bring up Mixin + Fabric, and hand off to the merged base's {@code Main.main} — which now runs plain vanilla boot
 * with the genuine loader trigger redirected to the kernel's own native registration window.
 *
 * <p>Server vs client differ only in the {@link Side}: env type, entry class, which lifecycle trigger is
 * redirected, and a few side-specific transform concessions. Everything else is identical, which is the point of
 * sharing it — the ecosystems' registration is side-independent.
 */
public final class KernelBoot {
	private KernelBoot() {
	}

	/** Used only when the base jar carries no {@code version.json}; the merged base is built from 26.2. */
	private static final String FALLBACK_GAME_VERSION = "26.2";

	/** The two boot sides. */
	public enum Side {
		SERVER(EnvType.SERVER, LifecycleHookInjector.SERVER_MAIN, true,
				"net.minecraft.server.dedicated.DedicatedServer"),
		CLIENT(EnvType.CLIENT, LifecycleHookInjector.CLIENT_MAIN, false,
				"net.minecraft.client.gui.screens.TitleScreen");

		final EnvType envType;
		final String entryClass;
		/**
		 * The class whose loading means "far enough along that the anchor census is worth reading".
		 *
		 * <p>Server: vanilla's own Main builds the PackRepository and the WorldStem BEFORE constructing this, so
		 * every server-side target the kernel cares about has already been through the chain.
		 *
		 * <p>Client: the title screen is the moment the player starts looking, and by then Minecraft, Options,
		 * ClientModLoader, PackRepository, Pack, ModList and GuiLayerManager have all been defined.
		 * KernelClientSmoke already resolves exactly this class as its "we are up" landmark.
		 */
		final String censusLandmark;
		/** The dedicated server rejects {@code --gameDir}; the client accepts it. */
		final boolean stripGameDir;

		Side(EnvType envType, String entryClass, boolean stripGameDir, String censusLandmark) {
			this.censusLandmark = censusLandmark;
			this.envType = envType;
			this.entryClass = entryClass;
			this.stripGameDir = stripGameDir;
		}

		LifecycleHookInjector injector() {
			return this == SERVER ? LifecycleHookInjector.forServer() : LifecycleHookInjector.forClient();
		}

		/**
		 * The neutral spelling of this side.
		 *
		 * <p>This enum is the BOOT side: it also carries the entry class and the arg-stripping rule, neither of
		 * which means anything to the ecosystems. {@link net.forbric.api.Side} is what crosses into them.
		 */
		public net.forbric.api.Side api() {
			return this == SERVER ? net.forbric.api.Side.DEDICATED_SERVER : net.forbric.api.Side.CLIENT;
		}
	}

	/**
	 * Runs the shared boot for {@code side}. {@code args} are the raw process args:
	 * {@code --gameJar}/{@code --runtimeJar}/{@code --libraryPath} are consumed here ({@code --runtimeJar} takes
	 * either one jar or several joined by the platform path separator); everything after {@code --}
	 * (and any unrecognized token) is forwarded to the game's {@code Main.main}.
	 */
	public static void launch(Side side, String[] args) throws Throwable {
		net.forbric.api.CompatibilityFindings.reset();
		net.forbric.kernel.discovery.MetadataFailures.reset();
		net.forbric.kernel.ui.CompatibilityDecision.reset();
		net.forbric.kernel.mixin.MixinCompatibility.reset();
		List<URL> owned = new ArrayList<>();
		List<String> gameArgs = new ArrayList<>();
		List<Path> runtimeJars = new ArrayList<>();
		List<Path> gameJars = new ArrayList<>();
		Path gameJar = null;
		String libraryPath = null;
		boolean afterSep = false;

		for (int i = 0; i < args.length; i++) {
			String a = args[i];
			if (afterSep) {
				gameArgs.add(a);
				continue;
			}
			switch (a) {
				case "--gameJar" -> {
					Path jar = new File(req(args, ++i, a)).toPath();
					if (gameJar == null) gameJar = jar;
					gameJars.add(jar);
					owned.add(jar.toUri().toURL());
				}
				case "--runtimeJar" -> {
					// Retained (not just owned): each ecosystem's runtime jar IS that ecosystem's own "mod" — FML
					// scans it for @EventBusSubscriber exactly like a mod jar, so the kernel must too.
					//
					// One flag may carry several jars, separated the way a classpath is. Repeating the flag still works
					// (every launch script in run/ does), but an installed profile must not depend on it: a launcher is
					// free to read game arguments as a flag-to-value map and keep only the last occurrence, which drops a
					// whole ecosystem's runtime and takes the game down on the first class that ecosystem owns.
					for (String entry : req(args, ++i, a).split(File.pathSeparator)) {
						if (entry.isBlank()) continue;
						Path jar = new File(entry).toPath();
						if (runtimeJars.contains(jar)) continue;
						runtimeJars.add(jar);
						owned.add(jar.toUri().toURL());
					}
				}
				case "--libraryPath" -> libraryPath = req(args, ++i, a);
				case "--" -> afterSep = true;
				default -> gameArgs.add(a);
			}
		}

		if (owned.isEmpty()) {
			System.err.println("forbric-kernel: no --gameJar given (need the merged base jar)");
			System.exit(2);
			return;
		}

		// The jars ARE the install, so they are checked by what is in them before anything is read out of them. A
		// "runtime" jar holding no Forge or NeoForge used to pass every step below with an empty answer and take the
		// boot down at KernelRuntimeClasses.verify, on stderr, with nothing in latest.log (issue #13).
		LaunchInputCheck.require(gameJars, runtimeJars);

		Path gameDir = extractGameDir(gameArgs, side.stripGameDir);
		String gameVersion = detectGameVersion(gameJar);

		// After a crash the last run attributed, offer to start without its suspects. Here, before arbitration,
		// because "start without" is a line in forbric-disabled.txt and arbitration is what reads that file.
		if (!CrashSuspectOffer.run(gameDir, side == Side.CLIENT)) return;

		// Two separate jars can declare the SAME mod id — inevitable the moment a Fabric pack and a NeoForge pack
		// are merged. MultiLoaderArbiter cannot see that (it is keyed by jar path), and left alone both jars enter
		// `owned` and shadow each other class-for-class, contribute each other's mixin configs, and register the
		// same content twice. Decide once here; both discoveries below skip the losers.
		// Pre-scan every declared nested candidate before either discovery discards a root. The later
		// arbitrateNested call verifies physical files against this same decision; it does not choose again.
		DuplicateModArbiter.Decision topLevelDupes =
				DuplicateModArbiter.arbitrate(gameDir.resolve("mods"), side.envType, gameVersion);

		// Forge/NeoForge mod jars (Mojmap-compiled like the merged base → load directly, no remap), plus the
		// libraries they nest at META-INF/jarjar/ — see extractForgeFamilyJarJar.
		// Learn what each carrier says its own version is BEFORE discovery reads the mods, so a mod whose
		// versionRange this instance cannot satisfy says so as it is discovered rather than failing later.
		EcosystemVersions.record(runtimeJars);
		ForgeFamilyMods forgeFamily = discoverForgeFamilyModJars(gameDir.resolve("mods"), topLevelDupes);

		// Presence, not loading. Every ecosystem keeps its own mod list, so a mod asking its own loader whether some
		// OTHER family's mod is installed is told no — and that answer is usually a compatibility branch, not a
		// display string. Published here, before the Fabric ecosystem is built, because that build reads it back.
		try {
			ModPresence.publishForgeFamily(PassiveSeeder.arbitratedForgeFamilyMods(gameDir.resolve("mods")));
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Presence] could not list the Forge-family mods for cross-ecosystem presence — a "
					+ "Fabric mod asking whether one of them is installed will be told no: %s", String.valueOf(t));
		}
		List<Path> modJars = new ArrayList<>(forgeFamily.jars());
		// The pre-scan has already selected every root and nested candidate. Consume those exact files;
		// the legacy extractor is only for the explicit arbitration-off mode.
		NestedCandidatePlan candidatePlan = DuplicateModArbiter.currentPlan();
		List<Path> nested = candidatePlan == null ? extractForgeFamilyJarJar(modJars, gameDir)
				: candidatePlan.nestedFiles();

		// Fabric discovery consumes the same preselected physical files and registers nothing yet. The union
		// below is checked against the plan before either loader builds containers or adds losing jars to the
		// classpath. When arbitration is explicitly disabled, both original discovery paths remain available.
		FabricModDiscovery fabricScan = scanFabricMods(side, gameDir, topLevelDupes, gameVersion, candidatePlan, modJars,
				nested);
		List<Path> allNested = new ArrayList<>(nested);
		for (Path jar : fabricScan.getClasspathJars()) {
			if (!modJars.contains(jar) && !nested.contains(jar)) allNested.add(jar);
		}
		final DuplicateModArbiter.Decision dupes = DuplicateModArbiter.arbitrateNested(side.envType, allNested);
		nested = nested.stream().filter(jar -> !dupes.suppressed(jar)).toList();

		nestedJarJarJars = List.copyOf(nested);
		modJars.addAll(nested);
		publishNestedPresence(nested);

		// THE MC LIBRARIES GO IN AHEAD OF THE MODS, and the order is the whole policy.
		//
		// They are owned rather than merely parent-visible because mods mixin into them
		// (fabric-dimension-api-v1 targets DataFixerUpper's TaggedChoice), and they sit AFTER the merged base and
		// the carriers so nothing shadows those. What changed is that they used to sit after the MOD jars too, and
		// URLClassLoader answers from the first URL that HAS the class — so a mod jar that bundles a copy of a
		// library the game already has WON.
		//
		// That is not hypothetical and it is not a degradation. PlayerDataSyncReloaded ships 226
		// com.google.gson.* classes at the UNSHADED package name, gson 2.10.1, against the 2.14.0 Minecraft 26.2
		// itself uses; its copy won, and the game died in SharedConstants.tryDetectVersion with
		// NoSuchMethodError JsonReader.setStrictness — reading version.json, before a single mod had loaded. The
		// same shape had already been paid for once as a hand-written pin: DelegationPolicy's NightConfig entry
		// exists because a CARRIER bundles an unshaded old copy.
		//
		// Ordering is the general form of that pin and needs no list. A class present only in a mod jar is
		// unaffected, because the library jars do not have it; a class present in BOTH now comes from the copy
		// the merged base was compiled against. That is also what the genuine loaders do — Knot and FML put
		// Minecraft's libraries on the same loader ahead of mods — so a mod relying on winning here was relying on
		// something that does not hold on its own platform either.
		List<Path> minecraftLibraries = libraryJars(libraryPath);
		int libCount = minecraftLibraries.size();

		// A nested mod's mixins are the same defect one level down. These jars already get everything else a
		// top-level mod gets — they are owned, and KernelModLoader scans them for @Mod, which is how whitenoise
		// (inside Mob Champions) is constructed — so leaving their configs out would be arbitrary. Pure libraries
		// declare none and cost one manifest read.
		List<KernelForgeFamilyMixins.ForgeMixinConfig> forgeMixinDecls = new ArrayList<>(forgeFamily.mixinConfigs());
		forgeMixinDecls.addAll(discoverForgeMixinConfigs(nested, "nested mod jar"));
		// And the runtimes' own. NeoForge's jar declares neoforge.mixins.json — two accessors its own code casts to
		// (BlockEntityTypeAddBlocksEvent on BlockEntityType.validBlocks, the biome/structure modifier re-sync on
		// MappedRegistry.registrationInfos) — and the runtime jars never went through discovery, so it was never
		// registered: every mod adding blocks to a block entity type got a ClassCastException from NeoForge itself
		// (tofucraft on the popular pack). The genuine loader registers it like any mod's.
		forgeMixinDecls.addAll(discoverForgeMixinConfigs(runtimeJars, "runtime jar"));
		// Every jar has been read and has an owner. A manifest that could not be read cost that jar alone; say
		// which, at the same weight as any other mod that did not load (see MetadataFailures).
		net.forbric.kernel.discovery.MetadataFailures.recordFindings(MultiLoaderArbiter::ownerOf, runtimeJars);

		// Fabric mods (+ extracted JiJ children). Also Mojmap on this game version. Creates the FabricLoader.
		List<Path> fabricJars = KernelFabricEcosystem.build(fabricScan, side.envType, gameDir, gameVersion,
				gameArgs.toArray(new String[0]), dupes, gameJar);

		// Game-side bundled libraries (MixinExtras) and the kernel's own runtime jar. The latter also carries
		// the kernel's client assets -- the Mods button's icon lives in it -- so its extracted path is handed to
		// the lifecycle for the client pack repository as well as to the class loader.
		List<Path> bundled = KernelBundledJars.extract(gameDir);
		owned = KernelOwnedClasspath.compose(owned, minecraftLibraries, modJars, fabricJars, bundled);
		KernelLifecycle.setKernelAssetJars(bundled);
		if (!KernelOwnedClasspath.bundledFirst()) {
