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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.forbric.api.DiscoveredMod;
import net.forbric.api.ModPresence;
import net.forbric.api.Side;
import net.forbric.api.Ecosystem;
import net.forbric.api.ForeignType;
import net.forbric.kernel.discovery.ForbricModDiscoverer;
import net.forbric.kernel.discovery.ModAnnotationScanner;
import net.forbric.kernel.metadata.forge.LanguageProviders;
import net.forbric.kernel.metadata.forge.ModsTomlParser;
import net.forbric.kernel.util.ForbricLog;
import net.forbric.kernel.util.Reflect;
import net.forbric.api.ModCatalog;

/**
 * Constructs discovered Forge-family {@code @Mod} classes natively — the M3 keystone for real mods.
 *
 * <p>For each mod jar it ASM-scans for {@code @Mod} classes ({@link ModAnnotationScanner}) and constructs each on
 * its own mod-event bus. No FancyModLoader discovery / module layer / sorting runs — the kernel owns construction.
 * Each constructed mod's bus is returned so the caller can fire {@code RegisterEvent} on it (flushing the mod's
 * {@code DeferredRegister}s).
 *
 * <p>The two Forge-family ecosystems are constructed differently and must not be confused:
 * <ul>
 *   <li><b>NeoForge</b> — a {@code BusBuilder} {@code IEventBus} + a kernel-manufactured {@code ModContainer}, with
 *       the ctor filled by parameter type ({@code IEventBus} → bus, {@code Dist} → dist, {@code ModContainer} →
 *       container);</li>
 * </ul>
 */
public final class KernelModLoader {
	private KernelModLoader() {
	}

	/**
	 * A constructed mod and what the caller needs to fire {@code RegisterEvent} at it: the NeoForge
	 * {@code IEventBus} the mod was constructed with.
	 */
	public record ConstructedMod(String modId, String className, Ecosystem family, Object bus) {}

	/**
	 * A NeoForge mod's identity: ONE bus and ONE {@code ModContainer} per mod id, shared by every {@code @Mod} class
	 * that declares that id. Genuine NeoForge works the same way — a mod has one container and one bus, not one per
	 * annotated class — and here it is also forced by {@code ModList.setLoadedMods}, whose {@code indexedMods} is
	 * built with {@code Collectors.toMap(ModContainer::getModId, identity())}: two containers sharing an id throw
	 * {@code IllegalStateException: Duplicate key}. Real mods do ship several (balm: {@code NeoForgeBalm} +
	 * {@code NeoForgeBalmClient}; FallingTree the same).
	 */
	public record NeoIdentity(Object bus, Object container) {}

	/**
	 * The NeoForge mods this kernel loaded, by mod id — their bus and container, as published to {@code ModList}.
	 *
	 * <p>Exposed so the lifecycle can post the FML setup events ({@code FMLCommonSetupEvent},
	 * {@code FMLClientSetupEvent}, {@code FMLLoadCompleteEvent}) at every mod, which needs exactly this pairing.
	 */
	public static Map<String, NeoIdentity> publishedNeoMods() {
		return publishedNeo;
	}

	private static volatile Map<String, NeoIdentity> publishedNeo = Map.of();

	/**
	 * The part of {@link #publishedNeoMods()} that declares no {@code @Mod} class — see
	 * {@link #declaredWithoutClass}. Its buses carry only what an {@code @EventBusSubscriber} put there, and the
	 * lifecycle needs them apart because it posts {@code RegisterEvent} per CONSTRUCTED mod, a list these are
	 * deliberately not in.
	 */
	public static Map<String, NeoIdentity> classlessNeoMods() {
		return classlessNeo;
	}

	private static volatile Map<String, NeoIdentity> classlessNeo = Map.of();

	/** The jar that declares each of {@link #classlessNeoMods()}. */
	private static volatile Map<String, Path> classlessNeoJars = Map.of();

	/**
	 * The one mod of {@link #classlessNeoMods()} that {@code jar} declares, or null when it declares none or several.
	 *
	 * <p>FML injects a jar's {@code @EventBusSubscriber} classes through the containers of that jar's mods, and a
	 * subscriber that names no mod id belongs to the container injecting it. A mod with no {@code @Mod} class gets
	 * a container like any other, so an unnamed subscriber in its jar is its own — which only this can say, since
	 * no {@code @Mod} class in that jar names it.
	 */
	static String soleClasslessNeoModIn(Path jar) {
		String only = null;
		for (Map.Entry<String, Path> entry : classlessNeoJars.entrySet()) {
			if (!entry.getValue().equals(jar)) continue;
			if (only != null) return null;
			only = entry.getKey();
		}
		return only;
	}



	/** Scans + constructs every {@code @Mod} in {@code modJars}. Best-effort per mod. */
	public static List<ConstructedMod> constructMods(ClassLoader cl, List<Path> modJars, Side side) {
		// Phase 1 — scan and ARBITRATE everything first, so the full NeoForge mod set is known before any mod's
		// constructor runs. A universal jar ships one @Mod per family; only the family that OWNS the jar may
		// construct, or the same mod initialises once per live ecosystem (see MultiLoaderArbiter).
		List<ModAnnotationScanner.ModClassInfo> claimed = new ArrayList<>();
		// modId -> the jar it came from, so its container can hand the mod its OWN files (see
		// KernelModContainerFactory.create's jar parameter).
		Map<String, Path> jarOfMod = new LinkedHashMap<>();
		for (Path jar : modJars) {
			List<ModAnnotationScanner.ModClassInfo> mods;
			try {
				mods = ModAnnotationScanner.scan(jar);
			} catch (Throwable t) {
				ForbricLog.warn("[Forbric/ModLoader] could not scan %s for @Mod classes", jar.getFileName());
				continue;
			}
			for (ModAnnotationScanner.ModClassInfo info : mods) {
				if (MultiLoaderArbiter.suppressedFor(jar, info.family)) continue;

				claimed.add(info);
				if (info.modId != null) jarOfMod.putIfAbsent(info.modId, jar);
			}
		}

		// What each jar's own manifest declares, by id. Every container below is described with it, and it is the
		// only list that knows a mod with no @Mod class at all. See declaredMods.
		Map<String, Declared> declared = declaredMods(modJars);

		// Phase 1b — put them in DEPENDENCY order. Until now this list was in jar-file-name order, alphabetically,
		// which is not an order at all: a mod whose jar sorts before a library it requires was constructed first
		// and called that library's API before the library had initialised. What comes back is an error inside the
		// library, attributed to the library, on a line that has nothing to do with the cause. Both real loaders
		// sort by dependency before they construct anything.
		claimed = orderByDependency(claimed);

		// Phase 2 — build every NeoForge mod's identity. Both families' identities are PUBLISHED below, before
		// phase 3, because a mod's constructor may ask its family's ModList about ITSELF, and against the kernel's
		// empty ModList that is fatal to that mod (Bookshelf: "Could not find mod 'bookshelf'" from getModBus;
		// Architectury: "Mod 'architectury' is not available!"). Publishing both sets up front also makes inter-mod
		// queries work regardless of construction order.
		//
		// The price, paid on both sides: between the publish and a given mod's own construction its container is
		// half-built — {@code getMod()} answers null where an unpublished list answered an honest empty Optional.
		// That is the better trade for the shape these mods actually use (resolve my container, take its bus), and
		// it is the shape to recognise if a mod ever complains that a NEIGHBOUR exists but has no instance.
		Map<String, NeoIdentity> neo = new LinkedHashMap<>();
		for (ModAnnotationScanner.ModClassInfo info : claimed) {

			String modId = safeId(info);
			if (neo.containsKey(modId)) continue;

			try {
				Object bus = KernelBusSupport.makeModBus(cl);
				Path jar = jarOfMod.get(modId);
				neo.put(modId, new NeoIdentity(bus,
						KernelModContainerFactory.create(cl, modId, bus, jar, declaredIn(declared, modId, jar))));
			} catch (Throwable t) {
				ForbricLog.warn("[Forbric/ModLoader] could not build ModContainer for NeoForge mod " + modId,
						Reflect.unwrap(t));
			}
		}
		// Presence aliases: a mod whose NeoForge jar lost cross-jar arbitration is still HERE — the winner's jar is
		// 98–100% the same classes — but without an entry ModList.get().isLoaded(id) answers false, and a NeoForge
		// mod that gates an integration on that check silently disables it. A container with no @Mod behind it:
		// identity only, since the winner already ran the mod's initialisation and registered its content.
		Map<String, NeoIdentity> aliases = new LinkedHashMap<>();
		for (DuplicateModArbiter.Alias alias
				: DuplicateModArbiter.current().aliasesFor(Ecosystem.NEOFORGE)) {
			if (neo.containsKey(alias.modId())) continue; // a real @Mod already owns it
			try {
				Object bus = KernelBusSupport.makeModBus(cl);
				aliases.put(alias.modId(),
						new NeoIdentity(bus, KernelModContainerFactory.create(cl, alias.modId(), bus)));
				ForbricLog.info("[Forbric/ModLoader] presence alias '%s' — its NeoForge jar lost arbitration, but "
						+ "the winning jar supplies the classes; ModList.isLoaded now answers", alias.modId());
			} catch (Throwable t) {
				ForbricLog.warn("[Forbric/ModLoader] could not alias " + alias.modId() + " into ModList",
						Reflect.unwrap(t));
			}
		}
		// Declared-only mods: a [[mods]] entry with no @Mod class behind it. That is a legal NeoForge mod — FML gives
		// every mod it lists an FMLModContainer, classes or not, and lowcodefml is mapped onto the same provider —
		// but the kernel built containers only for @Mod classes, so these were in no ModList at all. LibJF paid for
		// it: its Translate module is exactly this shape, LibJF enumerates ModList for `libjf:config` entry points,
		// and the config native NeoForge registers for it never existed here. So were the Modrinth datapack
		// wrappers (mr_fall_effects, mr_no_croptrample), which native NeoForge lists and version-checks.
		//
		// Unlike an alias these ARE the mod, not a stand-in for one that loaded under another jar: they stay in
		// publishedNeo so an @EventBusSubscriber naming them finds their bus and the lifecycle reaches it. They
		// are kept out of `neo`, whose ids are settled by what their constructors did — a mod with no constructor
		// cannot have one that threw.
		Set<String> taken = new LinkedHashSet<>(neo.keySet());
		for (ModAnnotationScanner.ModClassInfo info : claimed) taken.add(safeId(info));
		taken.addAll(aliases.keySet());
		Map<String, NeoIdentity> classless = new LinkedHashMap<>();
		Map<String, Path> classlessJars = new LinkedHashMap<>();
		for (Declared entry : declaredWithoutClass(declared, taken, Ecosystem.NEOFORGE)) {
			String modId = entry.mod().getId();
			try {
				Object bus = KernelBusSupport.makeModBus(cl);
				classless.put(modId, new NeoIdentity(bus,
						KernelModContainerFactory.create(cl, modId, bus, entry.jar(), entry.mod())));
				classlessJars.put(modId, entry.jar());
			} catch (Throwable t) {
				ForbricLog.warn("[Forbric/ModLoader] could not build a ModContainer for NeoForge mod " + modId
						+ ", which declares no @Mod class", Reflect.unwrap(t));
			}
		}
		if (!classless.isEmpty()) {
			ForbricLog.info("[Forbric/ModLoader] %d NeoForge mod(s) declare no @Mod class and now have a container "
					+ "of their own, as NeoForge gives every mod it lists — ModList, a config, and a bus for their "
					+ "@EventBusSubscriber classes (-D%s=off to go back): %s", classless.size(), CLASSLESS_SWITCH,
					classless.keySet());
		}
		classlessNeo = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(classless));
		classlessNeoJars = Map.copyOf(classlessJars);

		// Aliases go into ModList but NOT into publishedNeo: nothing must post setup events at a mod that has no
		// @Mod class here, and no caller should resolve an alias as if it were a constructed mod.
		Map<String, NeoIdentity> published = new LinkedHashMap<>(neo);
		published.putAll(classless);
		published.putAll(aliases);


		// Phase 3 — construct.
		List<ConstructedMod> built = new ArrayList<>();
		// @Mod classes this side is not supposed to construct. They keep their container — the mod IS installed,
		// and a neighbour asking about it must be told so — they simply do not run here.
		Set<String> otherSide = new LinkedHashSet<>();
		// NeoForge mod ids at least one of whose @Mod constructors threw, with the classes that threw. Kept apart
		// from otherSide because one id can be in both: see settleNeo.
		Map<String, List<String>> failedNeo = new LinkedHashMap<>();
		for (ModAnnotationScanner.ModClassInfo info : claimed) {
			// NeoForge's @Mod declares which sides it belongs to, and the kernel constructed every class on every
			// side regardless. Sodium's SodiumForgeMod says dist = {CLIENT}; on a dedicated server its constructor
			// reaches a client-only type and dies with a NoClassDefFoundError blamed on the mod.
			if (!info.runsOn(side.distName())) {
				recordNeoOutcome(info, false, null, otherSide, failedNeo);
				ForbricLog.info("[Forbric/ModLoader] @Mod %s (%s) declares it belongs to %s — not constructing it "
						+ "on %s, which is what its own annotation asks for", safeId(info), info.className,
						info.dists, side.distName());
				continue;
			}
			try {
				ConstructedMod mod = constructNeoFamilyMod(cl, info, neo.get(safeId(info)), side);
				built.add(mod);
			} catch (Throwable t) {
				recordNeoOutcome(info, true, t, otherSide, failedNeo);
				ForbricLog.warn("[Forbric/ModLoader] failed to construct @Mod " + info.className,
						Reflect.unwrap(t));
			}
		}
		// A mod whose constructor threw must come back OUT. This cannot un-tell it anything it learned during its
		// own construction, and it deliberately does not change isLoaded — ForeignModPresenceInjector rewrites
		// MinecraftForge's isLoaded to OR in the kernel's discovery-fed ModPresence, which answers for a mod that
		// is HERE whether or not its constructor ran. What it fixes is getModContainerById/getMods/size handing
		// back a container that passes instanceof FMLModContainer and yields a live-looking BusGroup that nothing
		// will ever post a RegisterEvent on.
		// The NeoForge twin of the withdrawal above, which only the MinecraftForge half used to have. A NeoForge
		// mod whose constructor threw stayed in ModList holding a container that passes instanceof FMLModContainer
		// and hands out a live-looking bus — so a LIBRARY mod resolving it and registering onto that bus was
		// registering into nothing, and a mod asking whether its dependency's container exists was told yes about
		// a mod that never finished loading.
		//
		// Presence aliases are deliberately kept: they have no @Mod class here by construction, so "did not
		// construct" is their normal state, not a failure. And the withdrawn mod's FILE entry stays in the by-id
		// map, because its jar really is present — what comes out is the container.
		//
		// Each id is settled from ALL of its @Mod classes (settleNeo): RollingGate's client-only class used to
		// stand in for its common class that threw, and the mod read as fine everywhere a player could look.
		Set<String> neoConstructed = new LinkedHashSet<>();
		for (ConstructedMod mod : built) {
			neoConstructed.add(mod.modId());
		}
		NeoSettlement settled = settleNeo(otherSide, neoConstructed, failedNeo.keySet());
		Set<String> neoBuilt = settled.kept();
		markPartlyConstructed(settled.degraded(), failedNeo);
		if (neoNeedsWithdrawal(neo.keySet(), neoBuilt)) {
			List<String> droppedNeo = new ArrayList<>();
			Map<String, NeoIdentity> keptNeo = keepConstructed(neo, neoBuilt, droppedNeo);
			publishedNeo = Map.copyOf(withClassless(keptNeo, classless));

			Map<String, NeoIdentity> republish = new LinkedHashMap<>(keptNeo);
			// A declared-only mod has no constructor that could have thrown, so it stays whatever else went.
			republish.putAll(classless);
			republish.putAll(aliases);
			// allowEmpty: "every NeoForge mod failed and there are no aliases" must publish an EMPTY list rather
			// than leave the full one standing.
			publishNeoModList(cl, republish, true);
			ForbricLog.warn("[Forbric/ModLoader] withdrew %d NeoForge container(s) from ModList — their @Mod "
					+ "constructor threw, so the bus those containers hand out is one nothing will ever post "
					+ "to %s", droppedNeo.size(), droppedNeo);
			for (String id : droppedNeo) markWithdrawn(List.of(id), constructorThrew(failedNeo.get(id)));
		}
		return built;
	}

	/**
	 * The published entries whose {@code @Mod} constructor actually ran, in their original order.
	 *
	 * <p>Both families withdraw the same way, and the reason is the same on both: a container left standing for a
	 * mod that never constructed passes {@code instanceof} and hands out a live-looking event bus that nothing
	 * will ever post to. So a library mod resolving it registers into nothing, and the failure surfaces much
	 * later somewhere that names neither mod.
	 *
	 * @param dropped receives the ids that come out, in order, for the log line
	 */
	static <T> Map<String, T> keepConstructed(Map<String, T> published, Set<String> constructed,
			List<String> dropped) {
		Map<String, T> kept = new LinkedHashMap<>();
		for (Map.Entry<String, T> entry : published.entrySet()) {
			if (constructed.contains(entry.getKey())) kept.put(entry.getKey(), entry.getValue());
			else dropped.add(entry.getKey());
		}
		return kept;
	}

	static final String NEO_TWIN_SWITCH = "forbric.neoTwinCtorFailure";

	/**
	 * The per-class half of what {@link #settleNeo} settles from: a class that does not run on this side, or a
	 * NeoForge class whose constructor threw, recorded under its id with the class that threw.
	 *
	 * <p>Pulled out of the construction loop so the wiring into settleNeo is tested, not only settleNeo: dropping
	 * either branch in the loop brought back RollingGate's masking while every settleNeo test stayed green.
	 */
	static void recordNeoOutcome(ModAnnotationScanner.ModClassInfo info, boolean runsHere, Throwable failure,
			Set<String> otherSide, Map<String, List<String>> failedNeo) {
		if (!runsHere) {
			otherSide.add(safeId(info));
			return;
		}
		if (failure != null) {
			failedNeo.computeIfAbsent(safeId(info), id -> new ArrayList<>()).add(info.className);
		}
	}

	/**
	 * The FAILED reason for a withdrawn NeoForge mod, naming the {@code @Mod} classes that threw when known. It starts
	 * with the plain reason, which is what {@code CompatibilityFindings} keys the constructor finding on.
	 */
	static String constructorThrew(List<String> classes) {
		return classes == null || classes.isEmpty() ? "its @Mod constructor threw"
				: "its @Mod constructor threw (" + String.join(", ", classes) + ")";
	}

	/**
	 * What the NeoForge side keeps after construction: {@code kept} is every id whose container stays in
	 * {@code ModList}, {@code degraded} the kept ids that also lost a constructor.
	 */
	record NeoSettlement(Set<String> kept, Set<String> degraded) {
	}

	/**
	 * Settles each NeoForge mod id from what happened to ALL of its {@code @Mod} classes, not to any one of them.
	 *
	 * <p>A mod may ship several {@code @Mod} classes under one id, and the common shape is a common class plus a
	 * {@code dist = CLIENT} one. This used to count an id as settled the moment ANY of its classes was other-side,
	 * and then compared the count with the number of published containers. RollingGate is exactly that shape:
	 * its common {@code RollingGate} threw, its client-only {@code RollingGateClient} put {@code rolling_gate} in
	 * the other-side set, the counts matched, and nothing was withdrawn or reported — the Mods screen and the
	 * compatibility report said OK while every rule it registers was missing, and {@code server_plus_plus}, which
	 * requires it, was told its dependency was live. notenoughcrashes has the same pair of classes.
	 *
	 * <ul>
	 *   <li>something of the id ran here: kept — and if something else of it threw, DEGRADED rather than
	 *       withdrawn, because one id is ONE container and ONE mod bus, so withdrawing would also cut the
	 *       listeners of the class that did construct (RollingGate on a client, where both classes run);</li>
	 *   <li>nothing ran here and nothing threw: every class is other-side, and the mod keeps its container as a
	 *       mod that is installed but does not run on this side — Sodium's CLIENT-only class on a server;</li>
	 *   <li>nothing ran here and something threw: withdrawn, whatever else it has on the other side (RollingGate
	 *       on a dedicated server).</li>
	 * </ul>
	 *
	 * <p>{@code -Dforbric.neoTwinCtorFailure=off} goes back to letting any other-side class stand for the id.
	 */
	static NeoSettlement settleNeo(Set<String> otherSide, Set<String> constructed, Set<String> failed) {
		Set<String> kept = new LinkedHashSet<>(constructed);
		Set<String> degraded = new LinkedHashSet<>();
		if ("off".equalsIgnoreCase(System.getProperty(NEO_TWIN_SWITCH, "on"))) {
			kept.addAll(otherSide);
			return new NeoSettlement(kept, degraded);
		}
		for (String id : otherSide) {
			if (!failed.contains(id)) kept.add(id);
		}
		for (String id : failed) {
			if (constructed.contains(id)) degraded.add(id);
		}
		return new NeoSettlement(kept, degraded);
	}

	/**
	 * Whether any published NeoForge container has to come out.
	 *
	 * <p>By membership, not by count. The count compared the kept set's SIZE with the published map's, and the
	 * kept set can hold an id the map never published — an other-side {@code @Mod} whose container could not be
	 * built at all — so each such id cancelled out one NeoForge mod whose constructor threw, and that dead
	 * container stayed. {@code -Dforbric.neoTwinCtorFailure=off} restores the count.
	 */
	static boolean neoNeedsWithdrawal(Set<String> published, Set<String> kept) {
		if ("off".equalsIgnoreCase(System.getProperty(NEO_TWIN_SWITCH, "on"))) return kept.size() != published.size();
		return !kept.containsAll(published);
	}

	/** One Forge-family {@code [[mods]]} entry and the jar whose manifest declares it. */
	record Declared(DiscoveredMod mod, Path jar) {
	}

	/**
	 * Every Forge-family mod the jars' own manifests declare, by id, first declaration winning.
	 *
	 * <p>Only the family that OWNS each jar counts: {@link MultiLoaderArbiter} has already given a universal jar to
	 * one family, and its other manifest describes a mod that is not being loaded as that family here.
	 *
	 * <p>This is the only description the kernel has of a mod nested inside another mod's jar. Discovery's
	 * {@code ModPresence} list is built from the jars in {@code mods/}, so the containers built for LibJF's twelve
	 * modules — every one of them a jar-in-jar — described themselves at version "0.0" with an empty
	 * {@code [modproperties]} table, and LibJF, which finds every one of its entry points in that table, found none
	 * of theirs.
	 */
	static Map<String, Declared> declaredMods(List<Path> modJars) {
		Map<String, Declared> out = new LinkedHashMap<>();
		// The seeder's discoverer: it has already parsed these jars, so they are not parsed (or logged) again here.
		ForbricModDiscoverer discoverer = PassiveSeeder.MANIFESTS;
		for (Path jar : modJars) {
			List<DiscoveredMod> mods;
			try {
				mods = discoverer.discoverJar(jar);
			} catch (Throwable t) {
				// The @Mod scan below reports an unreadable jar in its own words; one line per jar is enough.
				ForbricLog.debug("[Forbric/ModLoader] could not read the manifest of %s: %s", jar.getFileName(),
						String.valueOf(t));
				continue;
			}
			for (DiscoveredMod mod : mods) {
				if (!mod.getEcosystem().isForgeFamily()) continue;
				if (mod.getId() == null || mod.getId().isBlank()) continue;
				if (MultiLoaderArbiter.suppressedFor(jar, mod.getEcosystem())) continue;
				out.putIfAbsent(mod.getId(), new Declared(mod, jar));
			}
		}
		return out;
	}

	/** {@code -Dforbric.classlessModContainers=off} gives a mod with no {@code @Mod} class no container, as before. */
	static final String CLASSLESS_SWITCH = "forbric.classlessModContainers";

	/**
	 * The declared mods of {@code family} that no {@code @Mod} class claims and that the family's own loader
	 * would still give a container, in dependency order among themselves.
	 *
	 * <p>Which ones get a container is the loader's rule, not the kernel's. NeoForge's FancyModLoader gives one to
	 * every mod of a {@code javafml} file whether or not a class carries its id, and maps the deprecated
	 * {@code lowcodefml} onto the same provider (the native log says so for LibJF's own jar). Any other language
	 * belongs to a provider the kernel does not have, and inventing a container for it would claim a mod is
	 * loaded that the real loader might have refused.
	 *
	 * @param taken ids something else already answers for — an {@code @Mod} class of any family, or a presence
	 *              alias — which must not get a second container
	 */
	static List<Declared> declaredWithoutClass(Map<String, Declared> declared, Set<String> taken, Ecosystem family) {
		return declaredWithoutClass(declared, taken, family, entry -> languageOf(entry.jar(), family));
	}

	/** As above, with the language lookup handed in so a test can say what each jar declares. */
	static List<Declared> declaredWithoutClass(Map<String, Declared> declared, Set<String> taken, Ecosystem family,
			java.util.function.Function<Declared, String> languageOf) {
		if ("off".equalsIgnoreCase(System.getProperty(CLASSLESS_SWITCH, "on"))) return List.of();

		List<Declared> out = new ArrayList<>();
		Map<Path, String> languages = new java.util.HashMap<>();
		for (Declared entry : declared.values()) {
			if (entry.mod().getEcosystem() != family || taken.contains(entry.mod().getId())) continue;
			String language = languages.containsKey(entry.jar()) ? languages.get(entry.jar()) : languageOf.apply(entry);
			languages.put(entry.jar(), language);
			if (!getsAContainer(family, language)) {
				ForbricLog.debug("[Forbric/ModLoader] %s declares mod %s with no @Mod class under modLoader=%s, which "
						+ "%s gives no container of its own", entry.jar().getFileName(), entry.mod().getId(), language,
						family);
				continue;
			}
			out.add(entry);
		}
		if (out.size() < 2) return out;
		try {
			List<DiscoveredMod> mods = new ArrayList<>();
			for (Declared entry : out) mods.add(entry.mod());
			return ModConstructionOrder.sort(out, entry -> entry.mod().getId(), ModConstructionOrder.of(mods));
		} catch (Throwable t) {
			// An order is an improvement, never a precondition — the same rule orderByDependency keeps.
			return out;
		}
	}

	/**
	 * Whether {@code family}'s own loader builds a container for a class-less mod written in {@code language}.
	 *
	 * <p>The two families differ, and each is read off its own carrier. NeoForge's FancyModLoader builds an
	 * {@code FMLModContainer} for every mod of a {@code javafml} file and routes {@code lowcodefml} to the same
	 * provider. MinecraftForge's {@code ModLoader.buildMods} gives a {@code javafml} mod with no {@code @Mod} class
	 * the {@code fml.modloading.missingclasses} error instead, and only its {@code LowCodeModLanguageProvider}
	 * builds a container — a {@code LowCodeModContainer} — for a mod with no class at all.
	 */
	static boolean getsAContainer(Ecosystem family, String language) {
		if (language == null) return false;
		return family == Ecosystem.NEOFORGE
				&& (LanguageProviders.JAVA.equals(language) || LanguageProviders.LOW_CODE.equals(language));
	}

	/**
	 * The {@code modLoader} {@code jar}'s manifest for {@code family} declares, normalised; null when there is no
	 * such manifest or it cannot be read. A manifest that names none is a Java one.
	 */
	static String languageOf(Path jar, Ecosystem family) {
		String manifest = ForbricModDiscoverer.NEOFORGE_MANIFEST;
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
			java.util.zip.ZipEntry entry = zip.getEntry(manifest);
			if (entry == null) return null;
			try (java.io.InputStream in = zip.getInputStream(entry)) {
				return LanguageProviders.of(ModsTomlParser.parse(in));
			}
		} catch (Exception unreadable) {
			ForbricLog.debug("[Forbric/ModLoader] could not read the %s of %s: %s", manifest, jar.getFileName(),
					String.valueOf(unreadable));
			return null;
		}
	}

	/** {@code constructed} followed by {@code classless}, which never share an id. */
	static <T> Map<String, T> withClassless(Map<String, T> constructed, Map<String, T> classless) {
		Map<String, T> out = new LinkedHashMap<>(constructed);
		out.putAll(classless);
		return out;
	}

	/**
	 * The entry {@code jar} itself declares for {@code modId}, or null. Only the same jar's entry answers: a
	 * container is built from the jar its {@code @Mod} class came from, and it must not be described with another
	 * jar's claim to the same id.
	 */
	static DiscoveredMod declaredIn(Map<String, Declared> declared, String modId, Path jar) {
		Declared entry = modId == null ? null : declared.get(modId);
		return entry != null && entry.jar().equals(jar) ? entry.mod() : null;
	}

	/**
	 * The claimed {@code @Mod} classes in dependency order.
	 *
	 * <p>The order comes from what discovery already parsed out of every mod's own metadata — its requirements
	 * and its explicit load-order declarations — through {@link ModConstructionOrder}. Mods the registry has not
	 * heard of keep their place rather than being moved to either end.
	 */
	private static List<ModAnnotationScanner.ModClassInfo> orderByDependency(
			List<ModAnnotationScanner.ModClassInfo> claimed) {
		try {
			List<net.forbric.api.DiscoveredMod> known = new ArrayList<>(ModPresence.forgeFamilyMods());
			known.addAll(ModPresence.fabricMods());
			if (known.isEmpty()) return claimed;

			List<String> order = ModConstructionOrder.of(known);
			List<ModAnnotationScanner.ModClassInfo> sorted =
					ModConstructionOrder.sort(claimed, info -> info.modId, order);

			if (!sorted.equals(claimed)) {
				ForbricLog.info("[Forbric/Order] construction order is dependency order, not jar-file order — a mod "
						+ "that needs another to have run now does (-Dforbric.modOrder=name to go back): %s",
						sorted.stream().map(KernelModLoader::safeId).distinct().toList());
			}
			return sorted;
		} catch (Throwable t) {
			// An order is an improvement, never a precondition. Losing it must not cost the pack its mods.
			ForbricLog.warn("[Forbric/Order] could not order mods by dependency; using the order they were found in",
					Reflect.unwrap(t));
			return claimed;
