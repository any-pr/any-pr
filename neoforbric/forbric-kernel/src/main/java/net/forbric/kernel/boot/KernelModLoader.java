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
