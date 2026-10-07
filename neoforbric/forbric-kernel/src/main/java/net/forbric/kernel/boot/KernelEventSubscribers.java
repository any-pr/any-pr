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

import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import net.forbric.api.Side;
import net.forbric.api.Ecosystem;
import net.forbric.api.ModCatalog;
import net.forbric.api.ForeignType;
import net.forbric.kernel.classloading.ForbricClassLoader;
import net.forbric.kernel.discovery.ModAnnotationScanner;
import net.forbric.kernel.util.ForbricLog;
import net.forbric.kernel.util.Reflect;

/**
 * Registers a Forge-family mod's {@code @Mod$EventBusSubscriber} classes on the game event bus — the kernel's
 * native replacement for FML's {@code AutomaticEventSubscriber}.
 *
 * <p>Many Forge mods declare their game-event listeners not in the {@code @Mod} constructor but on a separate
 * class annotated {@code @Mod.EventBusSubscriber} whose static {@code @SubscribeEvent} methods FML auto-registers
 * during mod loading. The kernel does no FML scan, so those listeners would never fire (e.g. a mod's
 * {@code ServerTickEvent} handler). This ASM-scans each mod jar for the annotation and registers what it finds.
 *
 * <p><b>The two families are NOT interchangeable and each needs its own entry point.</b> An earlier version scanned
 * for both annotations, discarded which one matched, and handed every result to MinecraftForge's
 * {@code BusGroup.register(Lookup, Class)}. That was three bugs at once, all of them visible in the gate logs:
 *
 * <ul>
 *   <li><b>Per-CLASS registration on MinecraftForge is the wrong entry point.</b> {@code BusGroup.register} routes
 *       through {@code EventListenerFactory}, which throws {@code IllegalArgumentException: Only a single listener
 *       found in class …} when a subscriber declares exactly one listener — a performance hint, fatal here because
 *       the caller swallowed it. Genuine FML never takes that path: {@code AutomaticEventSubscriber} calls
 *       {@link #FML_EBS_LOGIC}, which builds listeners PER METHOD. gate-m4 lost {@code NoChatReports$Events} and
 *       {@code GeckoLibClient} to this.</li>
 *   <li><b>A NeoForge subscriber cannot be registered on a MinecraftForge {@code BusGroup}.</b> It carries
 *       {@code net.neoforged.bus.api.SubscribeEvent}, which MinecraftForge's scan does not recognise, so the class
 *       registers zero listeners and fails with {@code No listeners found}. gate-m4 lost collective's
 *       {@code RegisterCollectiveNeoForgeEvents}; gate-m7-neo lost all six of Architectury's, i.e. its ENTIRE event
 *       layer, silently, on a dedicated server.</li>
 *   <li><b>{@code Dist} was never consulted</b>, so a {@code @Dist.CLIENT} subscriber was loaded and registered on a
 *       dedicated server — noise at best, {@code NoClassDefFoundError} at worst.</li>
 * </ul>
 *
 * <p>So: the scan keeps which annotation matched, and each family goes to its own registrar. MinecraftForge reuses
 * FML's own {@code EventBusSubscriberLogic} rather than re-deriving its validation matrix (priority,
 * {@code Cancellable}, {@code alwaysCancelling}, boolean-returning predicates, 2-arg monitor listeners) — that is
 * the highest-drift code we could otherwise own, and it mints its own full-power lookup so {@link KernelGameLookup}
 * is not needed on that path. NeoForge reuses {@link #wireNeoSubscriber}, the per-method router this class already
 * had for NeoForge's own internal subscribers.
 */
public final class KernelEventSubscribers {
	private static final String SUBSCRIBE_NEO = "Lnet/neoforged/bus/api/SubscribeEvent;";
	private static final String EBS_NEO = "Lnet/neoforged/fml/common/EventBusSubscriber;";

	/**
	 * FML's own {@code @EventBusSubscriber} registration logic. The CLASS is package-private but this entry point is
	 * {@code public static void register(BusGroup, Class<?>)}, and it is exactly what
	 * {@code AutomaticEventSubscriber.inject} calls, so a {@code setAccessible(true)} reaches it.
	 */

	private KernelEventSubscribers() {
	}

	/**
	 * One {@code @EventBusSubscriber} class plus the annotation attributes that decide where its listeners go.
	 *
	 * @param family which ecosystem's annotation matched — the bit the old scan threw away
	 * @param dists  the declared {@code Dist[] value()}; EMPTY means "every side", not "no side"
	 * @param modId  the declared {@code modid()}, or null
	 * @param bus    the declared {@code bus()}, defaulting to {@code BOTH} when absent
	 */
	record Subscriber(String className, Ecosystem family, java.util.Set<String> dists,
			String modId, String bus, java.util.Set<String> subscribedEvents) {

		/** The five-field form callers and tests use when the subscribed event types are not needed. */
		Subscriber(String className, Ecosystem family, java.util.Set<String> dists, String modId, String bus) {
			this(className, family, dists, modId, bus, java.util.Set.of());
		}
	}

	/** Which {@code BusGroup} a MinecraftForge subscriber's listeners belong on. */
	enum BusChoice {
		/** {@code bus = FORGE} — the game bus. */
		DEFAULT,
		/** {@code bus = MOD} — the owning mod's own bus group. */
		MOD,
		/**
		 * {@code bus = BOTH}, the default. Pass {@code null} and let Forge route per event type — its
		 * {@code registerListener} opens with {@code if (busGroup == null)} and picks the mod bus for an
		 * {@code IModBusEvent}, {@code BusGroup.DEFAULT} otherwise. Passing DEFAULT here instead would strand
		 * every mod-bus listener.
		 */
		AUTO,
		/** {@code bus = MOD} but the owning mod has no bus group — anywhere else would be a lie that never fires. */
		SKIP
	}

	/** Whether a subscriber declaring {@code dists} runs on this side. An empty {@code value()} means every side. */
	static boolean matchesSide(java.util.Set<String> dists, Side side) {
		return dists.isEmpty() || dists.contains(side.distName());
	}

	/** Maps a MinecraftForge {@code bus()} attribute to the group to pass FML. See {@link BusChoice}. */
	static BusChoice busGroupChoice(String bus, boolean handlePresent) {
		if ("FORGE".equals(bus)) return BusChoice.DEFAULT;
		if ("MOD".equals(bus)) return handlePresent ? BusChoice.MOD : BusChoice.SKIP;
		return BusChoice.AUTO;
	}

	/** Scans + registers every guest mod's {@code @EventBusSubscriber} class, each on its own family's bus. */
	public static void registerAll(ClassLoader cl, List<Path> modJars, Side side) {
		if (!(cl instanceof ForbricClassLoader)) {
			ForbricLog.debug("[Forbric/EBS] not running under the sovereign loader — skipping @EventBusSubscriber");
			return;
		}
		ForbricClassLoader loader = (ForbricClassLoader) cl;

		NeoBusApi neo = NeoBusApi.resolve(cl);
		if (neo == null) {
			ForbricLog.debug("[Forbric/EBS] NeoForge's bus API is not present — skipping @EventBusSubscriber");
			return;
		}

		int neoMethods = 0;
		int skippedSide = 0;
		int skippedOwner = 0;
		int skippedFamily = 0;
		int skippedFailed = 0;
		for (Path jar : modJars) {
			List<Subscriber> subscribers = scan(jar);
			if (subscribers.isEmpty()) continue;
			List<ModAnnotationScanner.ModClassInfo> modsInJar = null; // scanned lazily, only if a modid() is missing

			for (Subscriber sub : subscribers) {
				// A universal jar ships one @EventBusSubscriber per family, and MultiLoaderArbiter has already
				// decided which family owns this jar. Registering both means the SAME handler runs twice for
				// every event both families post — double drops, double damage, double packets — and the mod has
				// no way to notice: each call looks like the only one.
				if (MultiLoaderArbiter.suppressedFor(jar, sub.family())) {
					skippedFamily++;
					ForbricLog.debug("[Forbric/EBS] skipping %s — %s does not own %s", sub.className(),
							sub.family(), jar.getFileName());
					continue;
				}
				if (!matchesSide(sub.dists(), side)) {
					skippedSide++;
					ForbricLog.debug("[Forbric/EBS] skipping %s — declares %s, running %s", sub.className(),
							sub.dists(), side.distName());
					continue;
				}
				if (sub.modId() == null || sub.modId().isBlank()) {
					if (modsInJar == null) modsInJar = scanModClasses(jar);
				}
				String modId = ownerModId(sub, modsInJar, () -> KernelModLoader.soleClasslessNeoModIn(jar));
				// A mod whose constructor threw registered nothing and initialised nothing; its listeners would
				// touch its own half-initialised classes the first time an event fires (wthit: NoClassDefFoundError
				// inside RegisterClientReloadListenersEvent, which killed the client). A native loader never gets
				// here — it stops at the constructor — so skipping them is the only honest continuation.
				if (didNotFinishLoading(modId)) {
					skippedFailed++;
					ModCatalog.mark(modId, ModCatalog.Status.DEGRADED, "its @EventBusSubscriber " + simpleName(sub.className())
							+ " was not registered — a mod in its jar did not finish loading, so its classes may be half-initialised");
					continue;
				}

				try {
					Object modBus = neoModBus(modId);
					if (modBus == null) skippedOwner++;
					neoMethods += wireNeoSubscriber(cl, sub.className(), modBus, neo);
				} catch (Throwable t) {
					registrationFailed(modId, sub.className(), Reflect.unwrap(t));
				}
			}
		}

		// gate-m4-canary greps "registered [0-9]+ @EventBusSubscriber" — keep this wording verbatim.
		int total = neoMethods;
		if (total > 0) {
			ForbricLog.info("[Forbric/EBS] registered %d @EventBusSubscriber class(es) on the game bus", total);
		}
		if (total > 0 || skippedSide > 0 || skippedOwner > 0 || skippedFamily > 0) {
			ForbricLog.info("[Forbric/EBS] %d NeoForge listener method(s); "
					+ "%d skipped as wrong-side, %d skipped (owning mod has no bus), %d skipped as the other "
					+ "family's half of a universal jar, %d skipped because the owning mod did not finish loading",
					neoMethods, skippedSide, skippedOwner, skippedFamily, skippedFailed);
		}
		// And this is about mixins that DID apply, to a point. Ninety individual lines on a client boot, none of
		// them totalled anywhere, for the state MixinFit's javadoc calls worse than either extreme.
		ForbricLog.info("%s", net.forbric.kernel.mixin.KernelGuestMixinAdapter.partialSummary());
		// And this is about questions the kernel was asked and could only answer empty — where "I do not know"
		// and "there is none" are the same value to the caller.
		ForbricLog.info("%s", PassiveSeeder.unmodelledSummary());
		// Everything above is about listeners. This is about a whole subsystem the merged game does not carry.
		CapabilityUseAudit.report();
	}

	/**
	 * A subscriber class that could not be registered: the cause chain is logged exactly as before, and when the
	 * owning mod is known its row says which class and that its listeners will not run. A subscriber no mod owns
	 * (a jar with no mod class beside it) marks nobody — the log line is the whole report then.
	 */
	static void registrationFailed(String modId, String className, Throwable real) {
		StringBuilder chain = new StringBuilder();
		for (Throwable x = real; x != null; x = x.getCause()) chain.append("\n      caused by: ").append(x);
		ForbricLog.warn("[Forbric/EBS] could not register " + className + chain, real);
		if (modId != null && !modId.isBlank()) {
			ModCatalog.mark(modId, ModCatalog.Status.DEGRADED, "its @EventBusSubscriber " + simpleName(className)
					+ " could not be registered — its listeners will not run");
		}
	}

	/** Whether the catalogue says {@code modId} is FAILED — its constructor or entrypoint threw. Unknown ids are not. */
	static boolean didNotFinishLoading(String modId) {
		if (modId == null) return false;
		// By JAR, not by id: wthit's one jar declares "waila" (whose class threw and left Waila half-initialised)
		// and "wthit" (whose client-only class constructed), and the subscriber that touched Waila belongs to
		// "wthit". A jar with a failed mod is a jar whose classes cannot be trusted to have initialised.
		String jar = null;
		for (ModCatalog.Entry e : ModCatalog.everything()) {
			if (e.modId().equals(modId)) {
				if (e.status() == ModCatalog.Status.FAILED) return true;
				jar = e.jar();
			}
		}
		if (jar == null || jar.isEmpty()) return false;
		for (ModCatalog.Entry e : ModCatalog.everything()) {
			if (jar.equals(e.jar()) && e.status() == ModCatalog.Status.FAILED) return true;
		}
		return false;
	}

	private static String simpleName(String className) {
		int dot = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
		return dot < 0 ? className : className.substring(dot + 1);
	}

	/** The mod bus of the NeoForge mod {@code modId}, or null when it was never published. */
	private static Object neoModBus(String modId) {
		if (modId == null) return null;
		KernelModLoader.NeoIdentity id = KernelModLoader.publishedNeoMods().get(modId);
		return id == null ? null : id.bus();
	}

	/**
	 * The mod that owns {@code sub}: its declared {@code modid()}, else the jar's single distinct {@code @Mod} id of
	 * the same family. Returns null when the jar declares several ids and the annotation named none — guessing would attach a
	 * mod's listeners to a sibling's bus.
	 *
	 * <p>A NeoForge jar with no {@code @Mod} class at all falls back to its single mod that has none: FML gives that
	 * mod a container, and the container injecting an unnamed subscriber is its owner (its mod-bus listeners then
	 * reach that mod's bus, as on NeoForge, rather than no bus). {@code -Dforbric.classlessModContainers=off}
	 * builds no such container, so the fallback finds none.
	 *
	 * @param soleClasslessNeo the jar's one NeoForge mod with no {@code @Mod} class, or null; asked only when the
	 *                         fallback is reached
	 */
	static String ownerModId(Subscriber sub, List<ModAnnotationScanner.ModClassInfo> modsInJar,
			java.util.function.Supplier<String> soleClasslessNeo) {
		if (sub.modId() != null && !sub.modId().isBlank()) return sub.modId();
		String only = null;
		if (modsInJar != null) {
			for (ModAnnotationScanner.ModClassInfo info : modsInJar) {
				if (info.family != sub.family() || info.modId == null) continue;
				// NeoForge can declare a common and a client @Mod entry class for the SAME id.
				// Ownership is ambiguous only between distinct mods, not between their entry classes.
				if (only != null && !only.equals(info.modId)) return null;
				only = info.modId;
			}
		}
		if (only != null || sub.family() != Ecosystem.NEOFORGE) return only;
		return soleClasslessNeo.get();
	}

