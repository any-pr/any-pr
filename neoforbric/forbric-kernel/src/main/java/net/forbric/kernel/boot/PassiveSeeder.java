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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

import net.forbric.api.DiscoveredMod;
import net.forbric.api.Ecosystem;
import net.forbric.api.ForeignType;
import net.forbric.api.ModPresence;
import net.forbric.api.Side;
import net.forbric.kernel.discovery.ForbricModDiscoverer;
import net.forbric.kernel.discovery.ModFileScanner;
import net.forbric.kernel.metadata.forge.FmlConfigElements;
import net.forbric.kernel.util.ForbricLog;

/**
 * Seeds the MINIMUM genuine-loader identity state that the merged base's patched-vanilla {@code <clinit>}s read,
 * WITHOUT running any genuine loader lifecycle.
 *
 * <p>The merged base is vanilla woven with Forge + NeoForge patches. Even a zero-mod boot trips over static
 * initializers that ask the genuine loaders "who am I?" — e.g. {@code SharedConstants.<clinit>} calls
 * {@code FMLEnvironment.isProduction()} → {@code FMLLoader.getCurrent()}, which throws
 * {@code "There is no current FML Loader"} if no {@code FMLLoader} instance exists. Normally
 * BootstrapLauncher/ModLauncher would have created one; the kernel does not run them.
 *
 * <p>This class establishes just the identity: an {@code FMLLoader} instance that answers dist / production /
 * classloader, made "current". It runs NO discovery, builds NO module layer, sorts NO mods — it only makes the
 * merged base's environment queries return sane answers. Everything is done reflectively THROUGH the kernel's
 * transforming loader so the seeded {@code FMLLoader} shares the exact class identity the game classes will read.
 *
 * <p>This is passive seeding, not lifecycle driving: it is the kernel-owned equivalent of "the environment exists",
 * the boundary the plan draws around universal jars as passive ABI carriers.
 */
public final class PassiveSeeder {
	private PassiveSeeder() {
	}

	/**
	 * FML's dev-vs-shipped flag, which for a Forbric instance is always "shipped".
	 *
	 * <p>It used to be a parameter, and {@code KernelBoot} filled it with {@code side == Side.SERVER} — so every
	 * client boot announced {@code production=false}, telling both ecosystems they were running out of a Gradle
	 * workspace. That is not a spelling mistake anyone would make with the axes named: it happened because the
	 * side and this flag were two adjacent booleans in the same signature.
	 *
	 * <p>{@code false} is what FML sets when the game is launched from a mod-development workspace: unobfuscated
	 * names, dev-only resource paths, relaxed checks. Nothing the kernel does resembles that — the gates and the
	 * installer both launch from built jars — and the dedicated server has been telling the truth about it all
	 * along. A constant rather than a parameter, because a parameter invites a caller to have an opinion, and the
	 * only opinion available here was the wrong one.
	 */
	private static final boolean PRODUCTION = true;

	/**
	 * Seeds every genuine-loader identity the merged base needs before the game entry runs. Best-effort per family.
	 *
	 * <p>{@code side} selects the seeded {@code Dist}. It is load-bearing: with the wrong dist, NeoForge's client
	 * code (and the integrated server's connection handshake) treats the client as a dedicated server — e.g. the
	 * local player's MODDED connection is rejected "Server is still starting".
	 *
	 * <p>The dev-vs-shipped flag is not a parameter — see {@link #PRODUCTION} for why it stopped being one.
	 */
	public static void seedAll(ClassLoader gameLoader, Path gameDir, Side side) {
		seedNeoForgeLoader(gameLoader, gameDir, side);
		seedNeoForgeModList(gameLoader);
		seedNeoForgePaths(gameLoader, gameDir);
		// NOTE: NeoForge baseline-registry registration is NOT done here — NeoForgeRegistriesSetup.<clinit> touches
		// game registries and throws "Not bootstrapped" pre-Main. It runs post-Bootstrap via KernelLifecycle
		// (the redirected ServerModLoader.load window). See KernelLifecycle.onServerModLoading.
	}

	/**
	 * Initializes NeoForge {@code FMLPaths} (GAMEDIR/CONFIGDIR/MODSDIR/…) so {@code FMLPaths.<X>.get()} returns a
	 * real path instead of null. {@code ConfigTracker.<clinit>} reads {@code FMLPaths.CONFIGDIR.get()} during the
	 * server-about-to-start hook. Pure path setup, no lifecycle.
	 */
	public static void seedNeoForgePaths(ClassLoader gameLoader, Path gameDir) {
		try {
			Class<?> fmlPaths = Class.forName(ForeignType.FML_PATHS.binary(Ecosystem.NEOFORGE), false, gameLoader);
			Method load = fmlPaths.getMethod("loadAbsolutePaths", Path.class);
			load.invoke(null, gameDir.toAbsolutePath());
			ForbricLog.debug("[Forbric/Seed] initialized NeoForge FMLPaths at %s", gameDir.toAbsolutePath());
		} catch (ClassNotFoundException absent) {
			ForbricLog.debug("[Forbric/Seed] NeoForge FMLPaths not present — skipping");
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Seed] could not init FMLPaths", unwrap(t));
		}
	}

	/**
	 * Seeds an EMPTY NeoForge {@code ModList} so {@code ModList.get()} returns an empty list instead of null. The
	 * merged base reads it early (e.g. {@code ResourcePackLoader.findResourcePacks} → {@code ModList.get().getModFiles()}
	 * when building the server pack repository). Zero mods = empty list. No-op if already present/absent.
	 */
	public static void seedNeoForgeModList(ClassLoader gameLoader) {
		try {
			Class<?> modList = Class.forName(ForeignType.MOD_LIST.binary(Ecosystem.NEOFORGE), false, gameLoader);
			Method get = modList.getMethod("get");
			if (get.invoke(null) != null) return;
			// of(modFiles, modInfos) constructs and installs the singleton INSTANCE.
			Method of = modList.getMethod("of", List.class, List.class);
			of.invoke(null, List.of(), List.of());
			Object instance = get.invoke(null);
			if (instance == null) {
				Field instanceField = modList.getDeclaredField("INSTANCE");
				instanceField.setAccessible(true);
				instance = of.invoke(null, List.of(), List.of());
				instanceField.set(null, instance);
			}
			// Populate mods/indexedMods/sortedContainers (null until mod loading) so iteration (e.g.
			// ModList.forEachModInOrder from ResourcePackLoader → ModLoader.postEvent) doesn't NPE. Zero mods.
			Method setLoadedMods = modList.getDeclaredMethod("setLoadedMods", List.class);
			setLoadedMods.setAccessible(true);
			setLoadedMods.invoke(instance, List.of());
			ForbricLog.debug("[Forbric/Seed] seeded empty NeoForge ModList (zero mods)");
		} catch (ClassNotFoundException absent) {
			ForbricLog.debug("[Forbric/Seed] NeoForge ModList not present — skipping");
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Seed] could not seed empty ModList", unwrap(t));
		}
	}

	/**
	 * Makes a NeoForge {@code FMLLoader} "current" (dist = DEDICATED_SERVER for the server), so
	 * {@code FMLLoader.getCurrent()} / {@code FMLEnvironment.isProduction()} answer instead of throwing.
	 * No-ops if a loader is already current or the class is absent.
	 */

	public static void seedNeoForgeLoader(ClassLoader gameLoader, Path gameDir, Side side) {
		seedNeoForgeLoader(gameLoader, gameDir, gameDir.resolve("mods"), side);
	}

	/**
	 * {@code modsDir} is the directory whose Forge-family jars become the seeded {@code LoadingModList} (see
	 * {@link #seedNeoForgeLoadingModList}). The 4-arg overload defaults it to {@code <gameDir>/mods}, which is the
	 * same directory {@code KernelBoot} walks for Forge-family discovery — the explicit parameter exists so the
	 * caller that already knows the mods dir passes ITS answer rather than re-deriving one that could drift.
	 */
	public static void seedNeoForgeLoader(ClassLoader gameLoader, Path gameDir, Path modsDir, Side side) {
		seedNeoForgeLoader(gameLoader, gameDir, modsDir, side, null);
	}

	/** Pass the detected game version into FML's own argument parser, rather than leaving VersionInfo null. */
	public static void seedNeoForgeLoader(ClassLoader gameLoader, Path gameDir, Path modsDir, Side side, String gameVersion) {
		try {
			Class<?> fmlLoader = Class.forName(ForeignType.FML_LOADER.binary(Ecosystem.NEOFORGE), false, gameLoader);

			Method getCurrentOrNull = fmlLoader.getDeclaredMethod("getCurrentOrNull");
			getCurrentOrNull.setAccessible(true);
			if (getCurrentOrNull.invoke(null) != null) {
				ForbricLog.debug("[Forbric/Seed] NeoForge FMLLoader already current — not re-seeding");
				return;
			}

			Class<?> distClass = Class.forName(ForeignType.DIST.binary(Ecosystem.NEOFORGE), false, gameLoader);
			String distName = side.distName();
			Object dist = Enum.valueOf(distClass.asSubclass(Enum.class), distName);

			// private FMLLoader(ClassLoader, String[], Dist, boolean production, Path gameDir)
			Constructor<?> ctor = fmlLoader.getDeclaredConstructor(
					ClassLoader.class, String[].class, distClass, boolean.class, Path.class);
			ctor.setAccessible(true);
			Object loader = ctor.newInstance(gameLoader, neoForgeVersionArguments(gameVersion), dist, PRODUCTION, gameDir);

			// The ctor may or may not self-register; makeCurrent() (guarded) ensures getCurrent() resolves.
			if (getCurrentOrNull.invoke(null) == null) {
				Method makeCurrent = fmlLoader.getDeclaredMethod("makeCurrent");
				makeCurrent.setAccessible(true);
				makeCurrent.invoke(loader);
			}

			seedNeoForgeLoadingModList(gameLoader, fmlLoader, loader, modsDir);

			ForbricLog.info("[Forbric/Seed] NeoForge FMLLoader seeded (dist=%s, production=%s) — "
					+ "environment identity only, no lifecycle", distName, PRODUCTION);
		} catch (ClassNotFoundException absent) {
			ForbricLog.debug("[Forbric/Seed] NeoForge FMLLoader not present — skipping");
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Seed] could not seed NeoForge FMLLoader identity", unwrap(t));
		}
	}

	static String[] neoForgeVersionArguments(String gameVersion) {
		List<String> args = new ArrayList<>();
		if (gameVersion != null && !gameVersion.isBlank()) {
			args.add("--fml.mcVersion"); args.add(gameVersion);
		}
		String neoVersion = net.forbric.kernel.metadata.forge.EcosystemVersions.provided("neoforge");
		if (neoVersion != null && !neoVersion.isBlank()) {
			args.add("--fml.neoForgeVersion"); args.add(neoVersion);
		}
		return args.toArray(String[]::new);
	}

	/**
	 * Seeds an EMPTY {@code LoadingModList} on the FMLLoader so {@code FMLLoader.getLoadingModList()} returns an
	 * empty list instead of throwing "The loading mod list isn't built yet" — the merged base reads it from
	 * {@code FeatureFlags.<clinit>} (via {@code FeatureFlagLoader.loadModdedFlags}) during {@code Bootstrap.bootStrap}.
	 * Zero mods = empty list. This seeds data, not lifecycle.
	 */
	private static void seedEmptyLoadingModList(ClassLoader gameLoader, Class<?> fmlLoader, Object loaderInstance) {
		try {
			Field field = fmlLoader.getDeclaredField("loadingModList");
			field.setAccessible(true);
			if (field.get(loaderInstance) != null) return; // already built

			Class<?> lmlCls = Class.forName(ForeignType.LOADING_MOD_LIST.binary(Ecosystem.NEOFORGE), false, gameLoader);
			// of(modFiles, gameLibraries, plugins, modInfos, issues, dependencies) — all empty for zero mods.
			Method of = lmlCls.getMethod("of", List.class, List.class, List.class, List.class, List.class, Map.class);
			Object empty = of.invoke(null, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
			field.set(loaderInstance, empty);
			ForbricLog.debug("[Forbric/Seed] seeded empty NeoForge LoadingModList (zero mods)");
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Seed] could not seed empty LoadingModList", unwrap(t));
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// LoadingModList — POPULATED, not empty. See seedNeoForgeLoadingModList's javadoc for the why.
	// ---------------------------------------------------------------------------------------------------------

	/** Escape hatch: {@code -Dforbric.seedLoadingModList=off} restores the pre-fix EMPTY list. */
	static final String SEED_SWITCH = "forbric.seedLoadingModList";

	/** Lazily-resolved {@code sun.misc.Unsafe}, the JDK-only fallback for constructor-free allocation. */
	private static volatile Object jdkUnsafe;
	/**
	 * Seeds a {@code LoadingModList} that actually CONTAINS the Forge-family mods, so a mod that resolves ITSELF
	 * through {@code FMLLoader.getLoadingModList()} finds itself.
	 *
	 * <p>The kernel used to seed a list that was structurally valid but empty ({@link #seedEmptyLoadingModList}),
	 * which answers the "does this thing exist" question the merged base's {@code <clinit>}s ask and nothing more.
	 * That is not what mods ask. A mod asks {@code getModFileById(myId).versionString()} (a version probe, rendered
	 * into a UI string) or walks {@code getMods()} looking for its own {@code ModInfo} to build a platform-neutral
	 * mod handle from. Against an empty list the first returns {@code null} and NPEs at the caller — for Iris that
	 * is inside {@code Minecraft.<init>}, i.e. a hard client crash with no world — and the second silently finds
	 * nothing, which mods read as "I am not installed, so this must be a dev environment" (LambDynamicLights then
	 * force-enables its dev-mode banner).
	 *
	 * <p>Why those probes run AT ALL on a Fabric-flavoured mod: multi-platform mods detect their platform by class
	 * presence ({@code Class.forName(ForeignType.FML_LOADER.binary(Ecosystem.NEOFORGE))}). On a normal instance exactly one
	 * family answers; on the merged base ALL of them do, so the NeoForge branch runs even for a jar that was built
	 * for Fabric. The kernel cannot make that branch not run, so it must make the branch's data true.
	 *
	 * <p>This stays passive seeding: it runs NO FancyModLoader discovery, builds no module layer, sorts nothing. It
	 * re-reads the mods dir with the kernel's own boot-side discoverer and states, in NeoForge's own data types,
	 * the set of Forge-family mods the kernel has already decided to load.
	 *
	 * <p>Falls back to the empty list — never throws, never fails the caller — when the switch is off, when there
	 * are no Forge-family mods (which keeps a zero-mod boot byte-identical to the old behaviour), or when anything
	 * at all goes wrong building the objects.
	 */
	static void seedNeoForgeLoadingModList(ClassLoader gameLoader, Class<?> fmlLoader, Object loaderInstance,
			Path modsDir) {
		seedNeoForgeLoadingModList(gameLoader, fmlLoader, loaderInstance, modsDir, KernelBoot.nestedJarJarJars());
	}

	/**
	 * @param nestedJars the Forge-family jar-in-jar files this boot extracted and put on the classpath — see
	 *                   {@link #arbitratedNestedForgeFamilyMods}. Null before extraction has run.
	 */
	static void seedNeoForgeLoadingModList(ClassLoader gameLoader, Class<?> fmlLoader, Object loaderInstance,
			Path modsDir, List<Path> nestedJars) {
		if ("off".equalsIgnoreCase(System.getProperty(SEED_SWITCH, "on"))) {
			ForbricLog.warn("[Forbric/Seed] -D%s=off — seeding an EMPTY NeoForge LoadingModList; mods that look "
					+ "themselves up through FMLLoader.getLoadingModList() will not find themselves", SEED_SWITCH);
			seedEmptyLoadingModList(gameLoader, fmlLoader, loaderInstance);
			return;
		}

		List<DiscoveredMod> mods;
		try {
			mods = arbitratedForgeFamilyMods(modsDir);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Seed] could not discover Forge-family mods for the NeoForge LoadingModList — "
					+ "falling back to the empty list", unwrap(t));
			seedEmptyLoadingModList(gameLoader, fmlLoader, loaderInstance);
			return;
		}
		// The Fabric mods go in TOO. Not to be loaded — nothing here loads anything — but because this list is
		// what answers "is mod X installed" for a Forge-family mod, and the honest answer includes the mods the
		// other ecosystem is running. Physics Mod reads exactly this seam (LoadingModList.getModFileById) to decide
		// whether to render through Sodium's pipeline or vanilla's; told no next to a live Fabric Sodium, it drew
		// its debris and ragdolls into a path Sodium no longer runs, so they were simply never visible.
		//
		List<DiscoveredMod> presence = new ArrayList<>(mods);
		Set<String> presenceIds = new LinkedHashSet<>();
		for (DiscoveredMod mod : mods) presenceIds.add(mod.getId());
		// The Forge-family mods that came out of another mod's jar go in beside them. See the method.
		List<DiscoveredMod> nested = arbitratedNestedForgeFamilyMods(nestedJars, presenceIds);
		presence.addAll(nested);
		for (DiscoveredMod mod : ModPresence.fabricMods()) {
			if (mod.getId() != null && mod.getSource() != null && presenceIds.add(mod.getId())) presence.add(mod);
		}

		if (presence.isEmpty()) {
			// Zero mods of any family: the old code path exactly, so gate-m1 / gate-m2b cannot move.
			seedEmptyLoadingModList(gameLoader, fmlLoader, loaderInstance);
			return;
		}

		// The one point in the boot where BOTH ecosystems' mods are known together, which is the only vantage from
		// which a cross-ecosystem requirement can be judged at all. Diagnostic only — it never changes what loads,
		// and it is caught here because a diagnostic must never be able to fail the window it reports on: the next
		// statement seeds the list every Forge-family mod resolves itself through.
		// The same vantage, for the same reason, one question further on: a player's Mods screen needs every
		// family's mods too, and every family's own screen can only list its own. Diagnostic-adjacent and caught
		// the same way — a screen that cannot be built must never cost the seeding below.
		try {
			KernelModCatalog.publish(presence, modsDir);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Catalog] could not build the unified mod list — the Mods screen will fall "
					+ "back to whatever one family's own registry knows", unwrap(t));
		}
		// The audit itself is NOT run here, and that is a fix rather than a rearrangement. Its second section
		// lists mixins that were written to attach to another mod and did not -- data that KernelGuestMixinAdapter
		// records while Mixin PARSES each config, which happens in KernelMixinBootstrap.init, roughly thirty lines
		// after the call that reaches this method. So the reader ran before the writer, every time, and
		// ForeignMixinBreaks.all() was always empty: that section of the dialog has never displayed anything.
		// gate-m20 could not see it because it drives the dialog with synthetic rows.
		//
		// Which is the same shape as everything else in this area: a diagnostic wired to a moment where its data
		// does not exist yet. The list is held here and KernelBoot asks for the audit once Mixin has run.
		pendingAudit = List.copyOf(presence);

		try {
			Field field = fmlLoader.getDeclaredField("loadingModList");
			field.setAccessible(true);
			if (field.get(loaderInstance) != null) return; // a genuine list exists — never overwrite it

			Object list = buildLoadingModList(gameLoader, presence);
			field.set(loaderInstance, list);

			StringBuilder ids = new StringBuilder();
			for (DiscoveredMod mod : presence) {
				if (ids.length() > 0) ids.append(", ");
				ids.append(mod.getId());
			}
			int forgeFamily = mods.size() + nested.size();
			ForbricLog.info("[Forbric/Seed] seeded NeoForge LoadingModList with %d mod(s) (%d Forge-family, %d "
					+ "Fabric for presence) — mods that resolve themselves through FMLLoader.getLoadingModList() "
					+ "(Iris' version probe, yumi/LambDynamicLights' mod lookup) find themselves, and mods that ask "
					+ "it about ANOTHER ecosystem's mod get the truth; %d of the Forge-family ones came out of "
					+ "another mod's jar. [%s]", presence.size(), forgeFamily, presence.size() - forgeFamily,
					nested.size(), ids);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Seed] could not seed a populated NeoForge LoadingModList — falling back to the "
					+ "empty list; mods that look themselves up through it will not find themselves", unwrap(t));
			seedEmptyLoadingModList(gameLoader, fmlLoader, loaderInstance);
		}
	}

	/**
	 * Every Forge-family mod in {@code modsDir}, filtered through {@link MultiLoaderArbiter} so a universal jar
	 * contributes under exactly the ONE ecosystem it was arbitrated to.
	 *
	 * <p>Without the filter a jar shipping all three manifests would appear twice here (its {@code mods.toml} and
	 * its {@code neoforge.mods.toml} are both truthfully reported by discovery), and — worse — a jar the arbiter
	 * handed to FABRIC would appear in the NeoForge list at all, telling the NeoForge side it owns a mod that is
	 * being initialised as a Fabric mod. The arbiter is the single boot-time policy for that question; this asks it
	 * rather than inventing a second answer.
	 *
	 * <p>Both Forge-family ecosystems are included, not just NeoForge: on the merged base a traditional
	 * MinecraftForge mod really IS loaded, so "is X present" must answer yes for it too. De-duplicated by mod id,
	 * first-wins, because {@code fileById} is a map and a duplicate id would otherwise silently shadow.
	 */
	static List<DiscoveredMod> arbitratedForgeFamilyMods(Path modsDir) throws Exception {
		List<DiscoveredMod> out = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		if (!Files.isDirectory(modsDir)) return out;

		ForbricModDiscoverer discoverer = MANIFESTS;
		List<Path> jars;
		try (var entries = Files.list(modsDir)) {
			jars = entries.filter(p -> p.getFileName().toString().endsWith(".jar"))
					.filter(Files::isRegularFile).sorted().toList();
		}

		// The seeded list must describe the jars this boot actually loaded. This walk is its own pass over mods/, so
		// it saw only MultiLoaderArbiter and reported a jar that cross-jar arbitration had already superseded — a
		// mod would then resolve itself through FMLLoader.getLoadingModList() and find the copy that is NOT running.
		DuplicateModArbiter.Decision dupes = DuplicateModArbiter.current();

		for (Path jar : jars) {
			if (dupes.suppressed(jar)) continue;
			// Per-jar, not one pass over the whole directory: an unreadable or malformed manifest anywhere in a real
			// mods folder must cost that one jar, not the entire seeded list.
			List<DiscoveredMod> declared;
			try {
				declared = discoverer.discoverJar(jar);
			} catch (Throwable t) {
				ForbricLog.debug("[Forbric/Seed] could not read %s for the NeoForge LoadingModList (%s) — skipping it",
						jar.getFileName(), String.valueOf(t));
				continue;
			}

			Ecosystem owner = MultiLoaderArbiter.ownerOf(jar);
			for (DiscoveredMod mod : declared) {
				if (!mod.getEcosystem().isForgeFamily()) continue;
				if (mod.getId() == null || mod.getId().isBlank() || mod.getSource() == null) continue;
				// owner == null means "no loader manifest at all", which cannot happen for a Forge-family mod; treat
				// it as unowned (keep) rather than as "not mine", per the arbiter's own contract.
				if (owner != null && owner != mod.getEcosystem()) continue;
				if (!seen.add(mod.getId())) continue;
				out.add(mod);
			}
		}
		return out;
	}

	/**
	 * The one discoverer the seeder's reads of {@code mods/} and of the nested jars go through, and that
	 * {@link KernelModLoader#declaredMods} reads the same jars through later. A discoverer remembers what it has
	 * parsed only for itself, so with one each the same jar was parsed again for every pass — and every parse logs
	 * its {@code [modproperties]} line again, which reads like a second mod declaring them.
	 */
	static final ForbricModDiscoverer MANIFESTS = new ForbricModDiscoverer();

	/** {@code -Dforbric.seedNestedMods=off} leaves the jar-in-jar Forge-family mods out of the seeded list again. */
	static final String NESTED_SWITCH = "forbric.seedNestedMods";

	/**
	 * The Forge-family mods inside {@code nestedJars} — the jar-in-jar files this boot extracted, already past
	 * cross-jar arbitration — each through the same two filters {@link #arbitratedForgeFamilyMods} applies to
	 * {@code mods/}, first-wins by id against everything already in {@code seen} (which this adds to).
	 *
	 * <p>{@link #arbitratedForgeFamilyMods} lists only the jars directly in {@code mods/}, and nothing descended into
	 * them, so a mod shipped inside another mod's jar was loaded and constructed but was not in the list a mod
	 * resolves itself or a neighbour through. Native NeoForge's list has every one of them: LibJF alone brings twelve
	 * ({@code libjf_base} … {@code libjf_web_v1}), and Fake Players brings commonnetworking. The one reader found
	 * that pays for the gap is LibJF's own entry-point lookup, which walks {@code getMods()} while {@code ModList}
	 * does not exist yet — at mixin-plugin time — and caches what it finds: its {@code libjf:asm} declarer,
	 * {@code libjf_data_manipulation_v0}, is one of the nested twelve.
	 *
	 * <p>The source is the extracted list itself, whose paths are the files the classes are served from, so a
	 * seeded file's contents and scan read the right bytes. Only the NeoForge list gains them: the list this feeds
	 * is presence, and {@code forgeFamilyMods} — which becomes MinecraftForge's handshake list — is left alone,
	 * because whether MinecraftForge announces a jar-in-jar mod is a separate question from whether it is here.
	 *
	 * @param nestedJars null when extraction has not run, which contributes nothing
	 */
	static List<DiscoveredMod> arbitratedNestedForgeFamilyMods(List<Path> nestedJars, Set<String> seen) {
		List<DiscoveredMod> out = new ArrayList<>();
		if (nestedJars == null || nestedJars.isEmpty()) return out;
		if ("off".equalsIgnoreCase(System.getProperty(NESTED_SWITCH, "on"))) {
			ForbricLog.info("[Forbric/Seed] -D%s=off — mods carried inside another mod's jar are left out of the "
					+ "NeoForge LoadingModList", NESTED_SWITCH);
			return out;
		}

		ForbricModDiscoverer discoverer = MANIFESTS;
		DuplicateModArbiter.Decision dupes = DuplicateModArbiter.current();
		for (Path jar : nestedJars) {
			if (jar == null || dupes.suppressed(jar)) continue;
			List<DiscoveredMod> declared;
			try {
				declared = discoverer.discoverJar(jar);
			} catch (Throwable t) {
				ForbricLog.debug("[Forbric/Seed] could not read nested %s for the NeoForge LoadingModList (%s) — "
						+ "skipping it", jar.getFileName(), String.valueOf(t));
				continue;
			}
			Ecosystem owner = MultiLoaderArbiter.ownerOf(jar);
			for (DiscoveredMod mod : declared) {
				if (!mod.getEcosystem().isForgeFamily()) continue;
				if (mod.getId() == null || mod.getId().isBlank() || mod.getSource() == null) continue;
				if (owner != null && owner != mod.getEcosystem()) continue;
				if (!seen.add(mod.getId())) continue;
				out.add(mod);
			}
		}
		return out;
	}

	/**
	 * Builds a genuine {@code LoadingModList} carrying a genuine {@code ModFileInfo} per jar and a genuine
	 * {@code ModInfo} per mod.
	 *
	 * <p><b>Concrete classes, not {@link Proxy}.</b> Both types are read back through CONCRETE-typed seams:
	 * {@code getModFileById} ends in {@code checkcast ModFileInfo}, and every consumer of {@code getMods()} gets a
	 * {@code List<ModInfo>} whose per-element access compiles to {@code checkcast ModInfo} (verified: yumi's
	 * {@code NeoModContainer.init} is exactly {@code getMods().forEach(lambda(…, ModInfo))}). A dynamic proxy
	 * therefore does not fail here, it fails at the READER with a ClassCastException — arbitrarily far from the
	 * seeding that caused it. So the real classes are allocated without a constructor and their private fields are
	 * filled, the same technique {@code KernelModContainerFactory} uses for {@code FMLModContainer}.
	 *
	 * <p><b>Why not the real constructors.</b> {@code LoadingModList.of(…)} demands concrete {@code ModFile}s,
	 * i.e. NeoForge's whole jar-contents/discovery pipeline; it is still used here, but with empty arguments, purely
	 * so every field it initialises (issues list, package index, plugin/game-library lists) is left exactly as
	 * NeoForge would leave it. {@code ModInfo}'s one public constructor is rejected deliberately: it re-parses the
	 * mod's metadata out of an {@code IConfigurable} and VALIDATES it, throwing {@code InvalidModFileException} when
	 * the id or the version does not match its patterns (the version one demands a leading digit), and its version
	 * path runs the metadata through {@code StringSubstitutor} against a {@code ModFile} we do not have, silently
	 * degrading to its {@code "1"} default. A mod's rendered version turning into "1" is precisely the bug this
	 * method exists to fix, so the fields are set directly instead.
	 *
	 * <p><b>Mutability</b> mirrors what the constructor produces, so nothing that mutates the list later breaks:
	 * {@code sortedList} and {@code modFiles} are MUTABLE ({@code new ArrayList<>(…)} / {@code Collectors.toList()}
	 * upstream), {@code fileById} is a mutable map ({@code Collectors.toMap} upstream), and each
	 * {@code ModFileInfo.mods} is IMMUTABLE ({@code Stream.toList()} upstream).
	 *
	 * <p><b>{@code allModFiles} is deliberately left EMPTY.</b> It is a {@code Set<IModFile>}, and its only readers
	 * ({@code contains}, {@code buildPackageIndex}) go straight on to {@code ModFile.getModuleDescriptor()} — a
	 * module descriptor that only exists once the jar has been read through NeoForge's own module machinery. An
	 * entry there would turn "the kernel does not track that" into an NPE inside the package index; an absent one
	 * merely answers "not there", which is what an empty list answers today.
	 */
	static Object buildLoadingModList(ClassLoader gameLoader, List<DiscoveredMod> mods) throws Exception {
		Class<?> lmlCls = Class.forName(ForeignType.LOADING_MOD_LIST.binary(Ecosystem.NEOFORGE), false, gameLoader);
		Class<?> fileInfoCls = Class.forName(ForeignType.MOD_FILE_INFO.binary(Ecosystem.NEOFORGE), false, gameLoader);
		Class<?> modInfoCls = Class.forName(ForeignType.MOD_INFO.binary(Ecosystem.NEOFORGE), false, gameLoader);

		Method of = lmlCls.getMethod("of", List.class, List.class, List.class, List.class, List.class, Map.class);
		Object list = of.invoke(null, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());

		// One ModFileInfo per JAR, N ModInfos inside it — a mods.toml may declare several [[mods]], and
		// ModFileInfo.versionString() is defined as its FIRST mod's version, so the grouping has to be per file.
		Map<String, List<DiscoveredMod>> byJar = new LinkedHashMap<>();
		for (DiscoveredMod mod : mods) {
			byJar.computeIfAbsent(mod.getSource(), key -> new ArrayList<>()).add(mod);
		}

		List<Object> fileInfos = new ArrayList<>();
		List<Object> modInfos = new ArrayList<>();
		Map<String, Object> fileById = new LinkedHashMap<>();

		for (Map.Entry<String, List<DiscoveredMod>> jar : byJar.entrySet()) {
			Object fileInfo = allocate(fileInfoCls);
			List<Object> ownMods = new ArrayList<>();
			for (DiscoveredMod mod : jar.getValue()) {
				Object modInfo = buildModInfo(gameLoader, modInfoCls, fileInfo, mod);
				ownMods.add(modInfo);
				modInfos.add(modInfo);
				fileById.putIfAbsent(mod.getId(), fileInfo);
			}
			fillModFileInfo(gameLoader, fileInfoCls, fileInfo, Path.of(jar.getKey()), jar.getValue().get(0),
					List.copyOf(ownMods), indexedForNeoForge(jar.getValue()));
			fileInfos.add(fileInfo);
		}

		indexUnderscoredIds(fileById);

		setInstanceField(lmlCls, "fileById", list, fileById);
		setInstanceField(lmlCls, "sortedList", list, new ArrayList<>(modInfos));
		setInstanceField(lmlCls, "modFiles", list, new ArrayList<>(fileInfos));
		return list;
	}

	/**
	 * Also indexes every dashed mod id under its underscored spelling, because that is the only spelling a
	 * NeoForge-side reader can ask with.
	 *
	 * <p>NeoForge mod ids may not contain {@code -} at all, so a mod written for NeoForge that wants to detect a
	 * Fabric module has to underscore the name — and it does so blind, at the call: Sodium's
	 * {@code NeoForgeRuntimeInformation.isModInLoadingList} is literally
	 * {@code getModFileById(id.replace('-', '_')) != null}, called with the string {@code "fabric-renderer-api-v1"}.
	 * The kernel seeds this list with the Fabric mods under their GENUINE ids, so that lookup asked for
	 * {@code fabric_renderer_api_v1}, got null, and Sodium concluded FRAPI was absent — it then installed a
	 * do-nothing renderer registrar, never registered {@code SodiumRenderer}, and left the FRAPI renderer slot
	 * open for Indigo to take. Producer on one side, consumer on the other, link silently dead.
	 *
	 * <p><b>Contained by measurement, not by hope:</b> {@code fileById} has exactly one writer (the constructor)
	 * and exactly one reader ({@code getModFileById}) in the merged base — verified on the bytecode — so an extra
	 * key can only make that one lookup answer for a spelling it previously refused. {@code getMods()} and
	 * {@code sortedList} are untouched, so nothing counts these twice and nothing lands in a handshake.
	 *
	 * <p>Aliases go in a SECOND pass, after every real id is indexed, and with {@code putIfAbsent}: a real mod
	 * that genuinely owns the underscored id keeps it, whatever order discovery happened to produce.
	 */
	private static void indexUnderscoredIds(Map<String, Object> fileById) {
