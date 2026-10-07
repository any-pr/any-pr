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
import java.util.List;
import java.util.Map;

import net.forbric.api.CompatibilityFinding;
import net.forbric.api.CompatibilityFindings;
import net.forbric.api.Side;
import net.forbric.api.Ecosystem;
import net.forbric.api.ForeignType;
import net.forbric.kernel.util.ForbricLog;
import net.forbric.kernel.util.Reflect;
import net.forbric.kernel.fabric.KernelFabricLoader;
import net.forbric.kernel.transform.FabricFreezePointInjector;
import net.forbric.api.ModCatalog;

/**
 * The kernel-owned server lifecycle hook that runs where the merged base used to call the genuine
 * {@code ServerModLoader.load(...)}.
 *
 * <p>{@link net.forbric.kernel.transform.LifecycleHookInjector} rewrites that call (in
 * {@code net.minecraft.server.Main.main}, after {@code Bootstrap.bootStrap}) to invoke
 * {@link #onServerModLoading(boolean)} instead — so the kernel drives its native registration at exactly the
 * point NeoForge's lifecycle would have, with the game fully bootstrapped (registries created) but before the
 * server proper starts. No FancyModLoader discovery / module layer / mod sorting ever runs.
 *
 * <p>This is the M3 native-registration window (plan phase P9): register the ecosystems' baseline registries and
 * content into the one shared registry set, then let the single vanilla freeze stand. Everything is invoked
 * reflectively through the kernel's transforming loader so class identity matches the game.
 */
public final class KernelLifecycle {
	private static volatile ClassLoader gameLoader;
	private static volatile List<Path> modJars = List.of();
	private static volatile List<Path> runtimeJars = List.of();

	// The NeoForge baseline mod's bus + container, captured during registration so the client step can construct
	// ClientNeoForgeMod on the same bus and route the game's mod-bus events to it.
	private static volatile Object baselineBus;
	private static volatile Object baselineContainer;
	/** {@code -Dforbric.neoTooltipAppenders=off} leaves NeoForge's item tooltip appenders unbuilt, as the kernel used to. */
	public static final String NEO_TOOLTIP_APPENDERS = "forbric.neoTooltipAppenders";

	private KernelLifecycle() {
	}

	/** Installed by the boot orchestrator so the injected game-side call can reach the transforming loader. */
	public static void bind(ClassLoader loader) {
		gameLoader = loader;
	}

	/** The Forge-family mod jars to construct in the registration window (set by the boot orchestrator). */
	/** The Forge-family mod jars this boot loaded (top-level and extracted nested), for game-side helpers. */
	public static List<Path> modJars() {
		return modJars;
	}

	public static void setModJars(List<Path> jars) {
		modJars = jars == null ? List.of() : jars;
	}

	/** The ecosystem runtime jars (forge/neoforge). Each is that ecosystem's OWN mod file — FML scans it too. */
	public static void setRuntimeJars(List<Path> jars) {
		runtimeJars = jars == null ? List.of() : jars;
	}

	/**
	 * The kernel's own game-side jars, which carry its client assets.
	 *
	 * <p>One asset in particular: the Mods button's icon. A GUI sprite is resolved through the resource manager
	 * like any other, so a texture the kernel ships is only findable if the kernel's jar is a pack the repository
	 * knows about — which is the same mechanism every mod's assets already travel on, pointed at ourselves.
	 */
	public static void setKernelAssetJars(List<Path> jars) {
		kernelAssetJars = jars == null ? List.of() : jars;
	}

	private static volatile List<Path> kernelAssetJars = List.of();

	/**
	 * Invoked from {@code net.minecraft.server.Main.main} (redirected from {@code ServerModLoader.load}) after
	 * {@code Bootstrap.bootStrap}. Drives native ecosystem registration. {@code dedicated} is the original argument.
	 */
	public static void onServerModLoading(boolean dedicated) {
		driveNativeRegistration(Side.DEDICATED_SERVER);
	}

	/**
	 * Invoked from {@code net.minecraft.client.main.Main.main} (redirected from {@code ClientModLoader.begin}) after
	 * {@code Bootstrap.validate} (which asserts {@code Bootstrap.bootStrap} already ran). Same native registration
	 * as the server — the ecosystems' baselines + content are side-independent; the Fabric ecosystem runs its
	 * {@code client} entrypoints rather than {@code server} ones, driven by {@code KernelFabricLoader}'s env type.
	 */
	public static void onClientModLoading() {
		driveNativeRegistration(Side.CLIENT);
	}

	/**
	 * Invoked first thing in the client's {@code Main.logEarlyException}, vanilla's handler for the three steps
	 * {@code Main.main} opens with. Vanilla prints the throwable to stderr and exits (status 249, 252, 251) without it
	 * leaving {@code main}, so this is the one chance to put the error that ended the game into {@code latest.log}.
	 * Never throws; vanilla's print and exit follow. Inserted by
	 * {@link net.forbric.kernel.transform.LifecycleHookInjector}.
	 */
	public static void onEarlyStartupFailure(Throwable failure) {
		CompatibilityLaunchBoundary.reportEscaping(failure);
	}

	private static void driveNativeRegistration(Side side) {
		ClassLoader cl = gameLoader != null ? gameLoader : Thread.currentThread().getContextClassLoader();
		ForbricLog.info("[Forbric/Lifecycle] kernel %s mod-loading window (native, no FancyModLoader) — "
				+ "registering ecosystem baselines", side.distName());
		// Step 0c (client only): load each carrier's own built-in translations. Their loader is called from
		// ClientModLoader.begin(), whose call site the kernel redirects here, so the table FMLTranslations and
		// ForgeI18n read was never filled and every FML-side string — the branding line under the logo, the loading
		// screen's continue button — rendered as its raw key. The dedicated server has its own entry point
		// (LanguageHook.loadLanguagesOnServer, per world) and is not this window's business.
		if (side.isClient()) CarrierLanguages.loadBuiltins(cl);
		// Step 1: register NeoForge's baseline registries (neoforge:fluid_type, …) into the root. Correctly timed
		// now (post-Bootstrap), unlike the pre-Main attempt which tripped "Not bootstrapped".
		PassiveSeeder.seedNeoForgeRegistries(cl);
		// Step 1b: mark NeoForge's VANILLA_SYNC_REGISTRIES (item/block/fluid/recipe_serializer/…) as client-syncing.
		// NeoForge's ByteBufCodecs registry-ID sync path (getSyncableRegistryOrThrow → RegistryManager
		// .isNonSyncedBuiltInRegistry → Registry.doesSync()) throws "Cannot use ID syncing for non-synced built-in
		// registry: minecraft:item" when the vanilla registries carry doesSync()=false. NeoForgeRegistriesSetup
		// normally sets these; the kernel doesn't run that setup, so mark them here (BaseMappedRegistry.setSync(true)).
		// Without this, the player logs in but the clientbound update_recipes packet fails to encode → disconnect.
		// Prefer NeoForge's own modifyRegistries handler: it does the setSync pass the kernel used to hand-roll AND
		// the five addCallback wirings nothing replaced — including the one that mirrors synced AttachmentTypes into
		// neoforge:synced_attachment_types, without which a mod using them kicks the player on join.
		if (!PassiveSeeder.applyNeoForgeRegistryModifications(cl)) markVanillaRegistriesSynced(cl);
		// Step 2: construct both ecosystem baselines + fire RegisterEvent so default content (e.g. minecraft:empty
		// FluidType, default attributes) registers, and run the Fabric main + side entrypoints in the same window.
		registerNeoForgeContent(cl, side);
		// Step 2b (client only): construct ClientNeoForgeMod on the baseline bus, so the game's
		// ModLoader.postEvent(<client mod-bus event>) — fired from ClientHooks.initClientHooks during
		// Minecraft.<init> for reload listeners, entity renderers, sprite sources, client extensions — has NeoForge's
		// built-in client handlers to reach.
		if (side.isClient()) registerNeoForgeClientContent(cl);
		// Step 2b2 (BOTH sides): put the baseline container into ModList, so ModLoader.postEvent — NeoForge's only
		// fan-out for the mod-bus events it posts ITSELF — reaches NeoForge's own listeners, not just the mods'.
		// This ran as part of step 2b, i.e. client-only, on the reasoning that only the client posts mod-bus events
		// from game code. The dedicated server posts one too, and it is the one that matters most over a socket:
		// NetworkRegistry.setup() posts RegisterPayloadHandlersEvent, whose NeoForge-internal listener
		// (NetworkInitialization.register, wired in step 2c2 to the baseline bus) registers every neoforge:* payload
		// type. With the baseline absent from ModList that event fanned out to the mods alone, so no dedicated
		// server the kernel ever booted could ENCODE a NeoForge payload — the first real client to join was dropped
		// with "Failed to encode packet 'clientbound/minecraft:custom_payload' (neoforge:recipe_content)". Eleven
		// gates certified those servers because none of them ever connected a client, and singleplayer never encodes.
		publishNeoBaselineInModList(cl);
		// Step 2c: load the NeoForge config specs the baselines and the just-constructed mods registered. Listeners
		// read CLIENT/COMMON values early (e.g. TagConventionLogWarningClient on ServerStartingEvent when entering a
		// singleplayer world reads a CLIENT value) — an unloaded spec throws "Cannot get config value before config
		// is loaded". This ran CLIENT-ONLY, on the reasoning that it left the proven server path alone; what it
		// actually left alone was a dedicated server with NO STARTUP or COMMON config loaded at all. A mod that
		// keys anything off its own config then has nothing to read: Balm sets a mod's active config from its
		// ModConfigEvent.Loading listener, so with the event never fired Waystones' getActive() —
		// Objects.requireNonNull(...) — threw NPE out of setupDynamicRegistries and the server died before Done.
		// Only the CLIENT type is client-only; NeoForge loads STARTUP and COMMON on both sides, and SERVER is
		// loaded separately, per-world, by ServerLifecycleHooks.handleServerAboutToStart.
		loadEarlyConfigs(cl, side);
		// Step 2c2: wire NeoForge's OWN @EventBusSubscriber classes from its runtime jar. NeoForge ships as a mod and
		// FML scans its jar like any other; the kernel scanned only mod jars, so ~10 internal subscribers (network,
		// attachments, configuration tasks, model data, …) never fired. Must precede step 2d — the network setup posts
		// its Register*PayloadHandlersEvent to exactly these subscribers.
		KernelEventSubscribers.registerNeoForgeInternal(cl, runtimeJars, baselineBus, side);
		// Step 3 USED TO BE HERE: registering mods' @EventBusSubscriber classes. It has moved INSIDE
		// registerNeoForgeContent, next to the constructors — see the comment at the new call site. Wiring them
		// here meant every registration-phase event had already been posted to nobody.
		// Step 3a: let mods declare their DATAPACK registries. These are not the registries RegisterEvent fills —
		// they are the per-world ones RegistryDataLoader builds from datapacks, and NeoForge collects them through
		// DataPackRegistryEvent.NewRegistry into DataPackRegistriesHooks. Nothing posted that event, so the list
		// stayed vanilla-only and lithostitched died the moment a world loaded: "Missing registry:
		// lithostitched:worldgen_modifier" out of RegistryAccess.lookupOrThrow, on the server tick loop. Must run
		// before any world is created; here is the first point where every mod's listeners are registered.
		//
		// Not here on a client whose Fabric mains run in Minecraft.<init>, though. This step initialises
		// RegistryDataLoader, whose initialiser runs Fabric mod code (WorldWeaver's datapack entrypoints ride a TAIL
		// injector there), and on native Fabric that code first runs at world load, after every main. Left here it
		// ran before any of them, with minecraft:root frozen: wover-biome's codec registry, which its own main
		// creates, was created from the initialiser instead, threw "Registry is already frozen", and poisoned
		// RegistryDataLoader and DataPackRegistriesHooks for the session — no world could be created, loaded or
		// joined, and NeoForge's data maps died with them. It moved when the client mains did (09d86de) and this
		// step did not. It runs at the end of onClientEntrypoints instead: after every main and client entrypoint,
		// the root frozen again, which is the state the dedicated server already declares in, cleanly.
		if (DatapackRegistryDeclaration.waitsForFabric(side, KernelFabricEcosystem.active(),
				KernelFabricEcosystem.mainsRunInConstructor())) {
			ForbricLog.info("[Forbric/Lifecycle] datapack-registry declaration waits for the Fabric main and client "
					+ "entrypoints in Minecraft.<init> — its initialisers run Fabric mod code, which must not run "
					+ "before those mains (-D%s=off to declare here)", DatapackRegistryDeclaration.DEFERRAL_SWITCH);
		} else {
			registerDataPackRegistries(cl);
		}
		// Step 3a2: open the game event buses — HERE, not after the setup lifecycle.
		//
		// Genuine NeoForge starts NeoForge.EVENT_BUS at the end of CommonModLoader.begin, immediately after its
		// "Config loading" task and before load() posts common setup (javap: getstatic NeoForge.EVENT_BUS;
		// invokeinterface IEventBus.start right after the second runInitTask). The kernel started it last, after
		// every setup phase — and IEventBus.post on a bus that has not started RETURNS SILENTLY (`getfield
		// shutdown; ifeq; return`). So a mod that posts its own API event during FMLConstructModEvent, RegisterEvent
		// or common setup — the "register with me" shape addon mods are built on — posted into nothing: no
		// listener ran, no error, no log, and the posting mod carried on with an empty result.
		//
		// Everything the kernel wires onto these buses (the Neo→Forge bridges inside startGameBuses, NeoForge's own
		// @EventBusSubscriber classes in step 2c2, the client reload bridge in 2c3) is already registered by this
		// point, and adding a listener to a started bus is allowed anyway.
		startGameBuses(cl, side);
		// Step 3b: post the FML setup lifecycle at every NeoForge mod. Genuine NeoForge produces these inside
		// CommonModLoader.load(), whose only client caller is ClientModLoader.finish() — which the kernel neuters
		// because it also drives the discovery/registration the kernel owns. Nothing replaced the setup phases, so
		// they never fired for anyone: AppleSkin registers its food tooltip from FMLClientSetupEvent
		// (preInitClient -> TooltipOverlayHandler.init -> NeoForge.EVENT_BUS.register), which is why the tooltip
		// stayed missing even after mod-bus delivery was fixed.
		fireModSetupLifecycle(cl, side);
		// Step 3b2: the late-config pass, in the one place it is honest. loadEarlyConfigs (step 2c) ran before
		// construction's own events, so a mod that registers a config from FMLConstructModEvent or common setup —
		// and a Fabric mod registering one through the ForgeConfigAPIPort — has a config that is registered and
		// never loaded. Reading it then throws "Cannot get config value before config is loaded" rather than
		// returning a default, from wherever the mod first asked. This opens only what has no loaded config yet, so
		// it cannot re-open what step 2c already did; on a pack where nothing registers late it says nothing.
		openLateConfigs(cl, side, "the mod setup lifecycle");
		// Step 3c: NOW close the payload registration phase. NetworkRegistry.setup() posts
		// RegisterPayloadHandlersEvent (payload types + codecs, incl. playToClient(neoforge:recipe_content)) and
		// ClientNetworkRegistry.setup() then posts the client-handler event and validates every to-client payload has
		// one — else the join negotiation rejects with "Incompatible client! (No Handler for …)".
		//
		// This used to run as step 2d, BEFORE the setup lifecycle, and that ordering was wrong: setup() flips
		// NetworkRegistry's `setup` flag, after which any registration throws "Cannot register payload <id> after
		// registration phase". Mods register payloads from FMLCommonSetupEvent — CreativeCore does, for itself and
		// for EnhancedVisuals — so on the Odyssey pack their payloads never registered and the player was dropped
		// with "Network Protocol Error" seconds after the world rendered. Genuine NeoForge closes the phase after
		// mod loading, which is what this now matches. On the CLIENT it moves later still, to onClientEntrypoints,
		// because client setup itself moved there.
		if (!side.isClient()) setupNeoForgeNetwork(cl, side);
	}

	/**
	 * Rebuilds NeoForge's blockstate→id map after the registration window closes.
	 *
	 * <p>Opening the window runs the vanilla registries' clear callback, and NeoForge's
	 * {@code BlockCallbacks.onClear} empties both that map and the {@code addedBlocks} set its {@code onBake} rebuilds
	 * from — so the bake only re-adds blocks REGISTERED INSIDE the window, i.e. none of vanilla's. The map is then
	 * empty and the first {@code clientbound/minecraft:block_update} cannot encode ("Can't find id for
	 * Block{minecraft:lava}"), kicking the player right after spawn. Re-add every block's states in registry order —
	 * the same order vanilla assigns ids in, so the numbering a remote client expects is reproduced. No-op if the map
	 * is already populated.
	 */
	private static void rebuildNeoForgeBlockStateIds(ClassLoader cl) {
		contentCall(cl, "rebuildBlockStateIds", "rebuild the NeoForge blockstate→id map");
		// The pot half of the same bake callback: NeoForge's flower pot table, which every pot lookup reads.
		contentCall(cl, "rebuildFlowerPotTable", "fill NeoForge's flower pot table");
		// Same moment, same reason: vanilla fills every block state's cache in Bootstrap, before any mod has
		// registered a block, and the kernel drives registration itself.
		contentCall(cl, "initialiseBlockStateCaches", "initialise the block state caches");
		// Third, and AFTER the id map is whole, because the mod pass it re-runs walks that map: a mod whose own
		// "every block exists now" pass ran before the kernel's last wave of registrations never saw those blocks.
		contentCall(cl, "initialiseBlockInfoCaches", "re-run the mods' whole-registry block passes");
	}

	/**
	 * Wires NeoForge's built-in network payloads: subscribes {@code NetworkInitialization#register} (its
	 * {@code @EventBusSubscriber} payload handler, which the kernel's mod-jar-only scan misses) to the baseline mod
	 * bus, then runs {@code NetworkRegistry.setup()} — which posts {@code RegisterPayloadHandlersEvent} through
	 * {@code ModLoader} to that bus, populating {@code PAYLOAD_REGISTRATIONS} so payloads like
	 * {@code neoforge:recipe_content} become sendable. Best-effort; failure only leaves payloads unregistered.
	 */
	private static void setupNeoForgeNetwork(ClassLoader cl, Side side) {
		// Two-phase, in this order: NetworkRegistry.setup() posts RegisterPayloadHandlersEvent (payload TYPES + codecs,
		// incl. playToClient(neoforge:recipe_content)); ClientNetworkRegistry.setup() then posts
		// RegisterClientPayloadHandlersEvent (the CLIENT HANDLERS) and validates every to-client payload has one —
		// else the join negotiation rejects with "Incompatible client! (No Handler for …)". Both events reach
		// NeoForge's own handlers only because step 2c2 wired its runtime-jar @EventBusSubscribers.
		invokeNetworkSetup(cl, ForeignType.NETWORK_REGISTRY.binary(Ecosystem.NEOFORGE));
		// The client half is client-only, as in genuine NeoForge (ClientModLoader runs it; ServerModLoader does not).
		// It used to run on the dedicated server too, and passed — vacuously, because no NeoForge payload type was
		// registered there for it to demand a handler for. The moment the server registered them (baseline in
		// ModList) it failed with "Some clientbound payloads are missing client-side handlers", correctly: the
		// handlers live in a Dist.CLIENT @EventBusSubscriber that step 2c2 rightly skips on a server.
		if (side.isClient()) invokeNetworkSetup(cl,
				"net.neoforged.neoforge.client.network.registration.ClientNetworkRegistry");
	}

	/** Runs a NeoForge {@code *NetworkRegistry.setup()} — it posts its Register*PayloadHandlersEvent via ModLoader. */
	private static void invokeNetworkSetup(ClassLoader cl, String registryClass) {
		try {
			Class<?> registry = Class.forName(registryClass, false, cl);
			registry.getMethod("setup").invoke(null);
			ForbricLog.info("[Forbric/Lifecycle] %s.setup() — payload handlers registered%s",
					registryClass.substring(registryClass.lastIndexOf('.') + 1), describePayloadRegistrations(registry));
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] " + registryClass + ".setup() failed — NeoForge payloads incomplete "
					+ "(join may fail 'No Handler for …')", unwrap(t));
		}
	}

	/**
	 * "; N payload type(s) registered {CONFIGURATION=a, PLAY=b}, NeoForge's own included: yes/no" — or "" when the
	 * class has no {@code PAYLOAD_REGISTRATIONS} (the client registry). Logged with the setup line because the one
	 * thing that line used to certify — "payload handlers registered" — was false for two months on every dedicated
	 * server the kernel ever booted: {@code setup()} had run, and had registered nothing of NeoForge's, because the
	 * event it posts fans out over {@code ModList} and the baseline container was not in it (see
	 * {@link #publishModBusDelivery}). Zero gates saw it, since singleplayer never encodes a packet. A count that
	 * says {@code PLAY=10, NeoForge's own included: no} would have.
	 */
	private static String describePayloadRegistrations(Class<?> registry) {
		try {
			java.lang.reflect.Field f = registry.getDeclaredField("PAYLOAD_REGISTRATIONS");
			f.setAccessible(true);
			java.util.Map<?, ?> byProtocol = (java.util.Map<?, ?>) f.get(null);
			java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
			boolean natives = false;
			int total = 0;
			for (java.util.Map.Entry<?, ?> e : byProtocol.entrySet()) {
				java.util.Map<?, ?> ids = (java.util.Map<?, ?>) e.getValue();
				counts.put(String.valueOf(e.getKey()), ids.size());
				total += ids.size();
				for (Object id : ids.keySet()) {
					if (String.valueOf(id).startsWith("neoforge:")) natives = true;
				}
				// The registered half of the channel census. Recorded here because this is the one walk over
				// PAYLOAD_REGISTRATIONS anywhere, and a second one would be a second thing to keep in step.
				net.forbric.kernel.interop.NetworkChannelCensus.registered(
						net.forbric.api.Ecosystem.NEOFORGE, ids.keySet());
			}
			return "; " + total + " payload type(s) registered " + counts + ", NeoForge's own included: "
					+ (natives ? "yes" : "NO");
		} catch (NoSuchFieldException clientRegistry) {
			return "";
		} catch (Throwable t) {
			return "; (could not read PAYLOAD_REGISTRATIONS: " + t + ")";
		}
	}

	/**
	 * Marks NeoForge's {@code VANILLA_SYNC_REGISTRIES} (item/block/fluid/recipe_serializer/…) as client-syncing via
	 * {@code BaseMappedRegistry.setSync(true)}, so the play-phase registry-ID codecs
	 * ({@code ByteBufCodecs.getSyncableRegistryOrThrow}) accept them instead of throwing "non-synced built-in
	 * registry". Best-effort; a failure only degrades registry-ID sync (logged, not fatal).
	 */
	private static void markVanillaRegistriesSynced(ClassLoader cl) {
		try {
			Class<?> setupCls = Class.forName("net.neoforged.neoforge.registries.NeoForgeRegistriesSetup", false, cl);
			java.lang.reflect.Field f = setupCls.getDeclaredField("VANILLA_SYNC_REGISTRIES");
			f.setAccessible(true);
			java.util.Set<?> regs = (java.util.Set<?>) f.get(null);
			Class<?> baseMapped = Class.forName("net.neoforged.neoforge.registries.BaseMappedRegistry", false, cl);
			java.lang.reflect.Method setSync = baseMapped.getDeclaredMethod("setSync", boolean.class);
			setSync.setAccessible(true);
			int n = 0;
			for (Object reg : regs) {
				if (baseMapped.isInstance(reg)) {
					setSync.invoke(reg, true);
					n++;
				}
			}
			ForbricLog.info("[Forbric/Lifecycle] marked %d vanilla registr(ies) client-syncing (doesSync=true)", n);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not mark vanilla registries synced (registry-ID sync may fail)",
					unwrap(t));
		}
	}

	/**
	 * Loads NeoForge's STARTUP/COMMON (and, on the client, CLIENT) config specs — registered by the baselines and by
	 * the mods constructed just before this — from the config dir, so {@code ModConfigSpec.ConfigValue.get()} reads
	 * work later in the lifecycle. Loading a spec is also what posts {@code ModConfigEvent.Loading}, which is how a
	 * config framework layered on NeoForge (Balm, for one) learns that a mod's config now has values; skip it and
	 * such a mod reads null forever.
	 *
	 * <p>SERVER is deliberately absent: it is per-world and belongs to {@code
	 * ServerLifecycleHooks.handleServerAboutToStart}, which the kernel does not excise.
	 *
	 * <p>Missing files are fine — NeoForge writes defaults. Best-effort per type; a failure is logged, not fatal.
	 */
	private static void loadEarlyConfigs(ClassLoader cl, Side side) {
		if ("off".equalsIgnoreCase(System.getProperty("forbric.earlyConfigs", "on"))) {
			ForbricLog.warn("[Forbric/Lifecycle] early config loading DISABLED (-Dforbric.earlyConfigs=off) — "
					+ "COMMON/CLIENT configs are not opened by the kernel; mods may keep "
					+ "defaults or read unloaded values");
			return;
		}
		// STARTUP is deliberately absent — see the game side, which explains what naming it would cost.
		List<String> types = side.isClient() ? List.of("COMMON", "CLIENT") : List.of("COMMON");
		try {
			configClass(cl).getMethod("loadEarly", List.class).invoke(null, types);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not load NeoForge configs", unwrap(t));
		}
	}

	/** The game-side half of config loading. */
	private static Class<?> configClass(ClassLoader cl) throws ClassNotFoundException {
		return Class.forName("net.forbric.kernel.runtime.KernelConfigLoad", true, cl);
	}

	/**
	 * Opens NeoForge configs that were registered after {@link #loadEarlyConfigs} had already run.
	 *
	 * <p>The early pass happens once, before mod content registration. A Fabric mod registering a config from a
	 * CLIENT entrypoint is therefore too late for it, and nothing else opens a non-STARTUP config — the carrier's
	 * {@code registerConfig} eagerly opens STARTUP only. The mod then reads a config that was registered and never
	 * loaded, and what it gets is not an empty config but "Cannot get config value before config is loaded",
	 * thrown from wherever it first asked. ShoulderSurfing asks from a mixin in {@code Minecraft.<init>}.
	 *
	 * <p>General on purpose: it fixes any late registrar, not the one that exposed it, and it cannot double-open
	 * because it opens only what has no loaded config yet. {@code ConfigTracker.loadConfigs} would have been the
	 * obvious call and is the wrong one — it re-opens every config of the type, and the carrier's second open
	 * warns and installs a SECOND file watcher, so every later edit of that file fires the reload twice.
	 *
	 * <p>Never SERVER: those are per-world and belong to the server-about-to-start hook, which loads them from the
	 * world directory. Opening them here would load them from the wrong place and overwrite them from the right
	 * one a moment later.
	 */
	private static void openLateConfigs(ClassLoader cl, Side side, String when) {
		if ("off".equalsIgnoreCase(System.getProperty("forbric.earlyConfigs", "on"))) return;
		try {
			Object result = configClass(cl).getMethod("openLate", List.class)
					.invoke(null, lateConfigTypes(side));
			if (result instanceof List<?> opened && !opened.isEmpty()) {
				ForbricLog.info("[Forbric/Lifecycle] opened %d late-registered NeoForge config(s) after %s %s — "
						+ "they were registered after the early pass, and nothing else would have loaded them",
						opened.size(), when, opened);
			}
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not open late-registered NeoForge configs", unwrap(t));
		}
	}

	/**
	 * Which config types {@link #openLateConfigs} may open, for a side.
	 *
	 * <p>SERVER is absent from both, and that absence is load-bearing rather than an oversight: a SERVER config is
	 * per-world and is loaded from the world directory by the server-about-to-start hook. Opening one here would
	 * load it from the global config directory, and the carrier's own warning for that ("Overwriting non-null
	 * config") is asserted absent by two gates.
	 */
	static List<String> lateConfigTypes(Side side) {
		return side.isClient() ? List.of("STARTUP", "COMMON", "CLIENT") : List.of("STARTUP", "COMMON");
	}

	/**
	 * Starts NeoForge's global game bus so game-event listeners dispatch.
	 *
	 */
	private static void startGameBuses(ClassLoader cl, Side side) {
		startBus(cl, "net.neoforged.neoforge.common.NeoForge", "EVENT_BUS",
				"net.neoforged.bus.api.IEventBus", "start", "NeoForge.EVENT_BUS");
	}

	/**
	 * Resolves one family's game bus and opens it, reporting absence and failure differently.
	 *
	 * @param holder the class holding the bus as a static field, {@code field} the field, {@code api} the type
	 *               declaring the start method, {@code start} that method, {@code label} what to call it in the log
	 */
	private static void startBus(ClassLoader cl, String holder, String field, String api, String start,
			String label) {
		Object bus;
		try {
			bus = Class.forName(holder, false, cl).getField(field).get(null);
		} catch (ClassNotFoundException | NoSuchFieldException | LinkageError absent) {
			ForbricLog.debug("[Forbric/Lifecycle] no %s on this runtime — nothing to start", label);
			return;
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not read " + label + " — every game-event listener of that "
					+ "family is on a bus nothing will dispatch", unwrap(t));
			return;
		}
		try {
			Class.forName(api, false, cl).getMethod(start).invoke(bus);
			ForbricLog.info("[Forbric/Lifecycle] started %s (game events now dispatch)", label);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] " + label + " is present but did NOT start — every game-event "
					+ "listener of that family, in every mod, is now on a bus nothing dispatches", unwrap(t));
		}
	}

	/**
	 * The NeoForge mod buses the registry events go to, each once: every constructed mod's, then every
	 * declared-only mod's.
	 *
	 * <p>Deduped by IDENTITY: a NeoForge mod has ONE bus shared by all its {@code @Mod} classes (balm ships
	 * NeoForgeBalm + NeoForgeBalmClient, FallingTree the same), so a per-entry list would fire RegisterEvent twice
	 * on that bus and register the mod's content twice.
	 *
	 * <p>The declared-only mods — a {@code [[mods]]} entry with no {@code @Mod} class, see
	 * {@link KernelModLoader#classlessNeoMods()} — had nothing constructed, but FML posts the registry events to
	 * every container it lists, and for such a mod an {@code @EventBusSubscriber} registering its content from
	 * RegisterEvent is the whole of its code.
	 */
	static List<Object> registrationBuses(List<KernelModLoader.ConstructedMod> mods,
			java.util.Collection<KernelModLoader.NeoIdentity> classless) {
		List<Object> buses = new ArrayList<>();
		java.util.Set<Object> seenBuses = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (KernelModLoader.ConstructedMod m : mods) {
			if (m.bus() != null && seenBuses.add(m.bus())) buses.add(m.bus());
		}
		for (KernelModLoader.NeoIdentity identity : classless) {
			if (identity.bus() != null && seenBuses.add(identity.bus())) buses.add(identity.bus());
		}
		return buses;
	}

	/**
	 * Constructs NeoForge's baseline mod ({@code NeoForgeMod}) on a fresh mod-event bus — which registers its
	 * {@code DeferredRegister}s — then fires {@code RegisterEvent} per registry so those DeferredRegisters flush
	 * their default content (the empty/water/lava FluidTypes, default attributes, …). A single unfreeze/freeze
	 * window surrounds the registration; because the kernel drives ONE pass (no dual-ecosystem refreeze), the
	 * "Tags not bound" wall of the old weld does not arise.
	 */
	private static void registerNeoForgeContent(ClassLoader cl, Side side) {
		// Whether the registration window was opened, and so whether the finally below owes it a close.
		boolean closeWindow = false;
		try {
			Class<?> distClass = Class.forName(ForeignType.DIST.binary(Ecosystem.NEOFORGE), false, cl);
			Object dist = Enum.valueOf(distClass.asSubclass(Enum.class), side.distName());

			// The NeoForge baseline mod on its own bus. Captured so the client step can add ClientNeoForgeMod to
			// the same bus + route the game's mod-bus events to its container.
			Object bus = KernelBusSupport.makeModBus(cl);
			Object container = KernelModContainerFactory.create(cl, "neoforge", bus);
			baselineBus = bus;
			baselineContainer = container;
			Class<?> neoForgeMod = Class.forName("net.neoforged.neoforge.common.NeoForgeMod", false, cl);
			Class<?> iEventBus = Class.forName("net.neoforged.bus.api.IEventBus", false, cl);
			Class<?> modContainer = Class.forName(ForeignType.MOD_CONTAINER.binary(Ecosystem.NEOFORGE), false, cl);
			neoForgeMod.getConstructor(iEventBus, distClass, modContainer)
					.newInstance(bus, dist, container);
			ForbricLog.info("[Forbric/Lifecycle] constructed NeoForge baseline mod on a native bus (dist=%s)",
					side.distName());
			Object baselineBus = bus;

			// Real Forge-family @Mods, each on its own bus.
			List<KernelModLoader.ConstructedMod> mods =
					KernelModLoader.constructMods(cl, modJars, side);

			// Load the config specs those constructors just registered, BEFORE any RegisterEvent fires. Genuine
			// NeoForge loads STARTUP/COMMON right after construction and only then posts the registry events, and
			// mods rely on that: Mob Champions' MobChampionsEffects.<clinit> runs from its RegisterEvent listener
			// and reads a config value, so with the load still pending it threw "Cannot get config value before
			// config is loaded" — which, before the isolation below, aborted the whole window and left even the
			// NeoForge baseline unregistered (later surfacing as an unbound neoforge:fluid_type/water). Re-run after
			// the client baseline in driveNativeRegistration too, for specs registered later; loading twice is
			// harmless (each type is attempted independently and a redundant load is swallowed).
			// Wire every mod's @EventBusSubscriber classes NOW, while the registration window is still ahead of
			// them. Genuine FML does this inside ModContainer.constructMod(), i.e. before registry init, so a
			// subscriber-declared handler is attached by the time any registration event is posted. The kernel used
			// to do it much later, after registerNeoForgeContent had already returned, and the cost was silent:
			// earthmobsmod and bagus_lib declare their EntityAttributeCreationEvent handlers on a class-level
			// @EventBusSubscriber, so CommonHooks.modifyAttributes below posted to an empty bus and every one of
			// their entities came out attribute-less — 2250 "Entity <id> has no attributes" errors per freeze, and
			// mobs that cannot spawn. The same was true of any RegisterEvent handler declared that way.
			//
			// AFTER loadEarlyConfigs, not before: this class-loads every subscriber, and a <clinit> that reads a
			// config value must not run ahead of the specs. Still strictly later than genuine NeoForge, which loads
			// these classes during construction — so nothing that survives real NeoForge can fail for being early
			// here. Both game buses stay unstarted until startGameBuses, so early registration is buffered, not lost.
			//
			// Isolated: this now sits inside registerNeoForgeContent's try, and an escape would abort the whole
			// registration window and be reported as "could not register ecosystem content", blaming the wrong
			// thing entirely.
			try {
				KernelEventSubscribers.registerAll(cl, modJars, side);
			} catch (Throwable t) {
				ForbricLog.warn("[Forbric/Lifecycle] could not wire guest @EventBusSubscriber classes — mods that "
						+ "declare their registry or attribute handlers there will not be reached", unwrap(t));
			}

			// FMLConstructModEvent, the phase genuine FML posts to each container the moment it is built. The
			// kernel constructed the mods and went straight on, so anything a mod does there — and it is the
			// earliest mod-bus phase there is — never happened. Posted after the subscribers are wired, so a
			// handler declared on an @EventBusSubscriber receives it too.
			fireSetupPhase(cl, KernelModLoader.publishedNeoMods(), ForeignType.FML_CONSTRUCT_MOD_EVENT, "construct");

			// The mods' RegisterEvent stream runs inside the one unfreeze/freeze window below.
			List<Object> buses = new ArrayList<>();
			buses.add(baselineBus);
			buses.addAll(registrationBuses(mods, KernelModLoader.classlessNeoMods().values()));

			// Capture the post-Bootstrap vanilla registry state for NEOFORGE only, before the window opens. NeoForge's
			// unfreeze clear-callback empties its blockstate→id map, and BlockCallbacks.onBake only re-adds blocks that
			// onAdd saw during the window (none of vanilla's) — so without a snapshot to restore from, the map stays
			// empty and the first clientbound block_update cannot encode ("Can't find id for Block{minecraft:lava}").
			// NOT MinecraftForge's: its vanillaSnapshot LOCKS the vanilla wrappers, and every later register in this
			// window then throws "Can not register to a locked registry" (gate-m1 RED).
			invokeGameDataOn(cl, ForeignType.GAME_DATA.binary(Ecosystem.NEOFORGE), "vanillaSnapshot");
			unfreeze(cl);
			// From here the registries are OPEN, and everything that closes them again lives in the finally below.
			// It used to live inline at the end of this try, so anything that threw in between — a Class.forName for
			// a carrier type that was renamed, NeoForgeRegistries failing to initialise, an Error escaping the
			// baseline or the Fabric entrypoints — left every registry writable for the rest of the run and skipped
			// linkBlockItems, the blockstate-id rebuild, the creative-tab sort and the registriesLoaded latch. The
			// symptoms are the ones this file already documents one by one: Block.asItem() returns AIR so a mod's
			// creative tab is empty and its blocks cannot be picked, the first block_update fails to encode with
			// "Can't find id for Block{minecraft:lava}" and kicks the player at spawn, and mods gating on
			// areRegistriesLoaded() refuse to register render layers. The only report was one WARN saying the
			// registration window "could not register ecosystem content", which names none of that.
			closeWindow = true;
			// MOD buses only — buses.get(0) is the baseline, whose registries PassiveSeeder already registered at
			// seed time; posting there re-collects them and fill() dies on "Attempted duplicate registration".
			KernelFabricEcosystem.initializeSpectreConfigs();
			postNeoNewRegistryEvent(cl, buses.subList(1, buses.size()));
			// Isolated for the same reason KernelEventSubscribers.registerAll above is, and this one is wider.
			// fireRegisterEvents resolves a GAME-side class reflectively, so a LinkageError inside it escapes to
			// the outer catch and skips EVERYTHING below: the traditional-Forge baseline, the Fabric mods' main
			// entrypoints, the attribute events, the spawn-placement event, BlockEntityTypeAddBlocksEvent and the
			// modded creative-tab categories. The one WARN that reported it said "could not register ecosystem
			// content", which names none of that -- it blames the window for what one call inside it did.
			int n = 0;
			try {
				n = fireRegisterEvents(cl, buses);
			} catch (Throwable t) {
				ForbricLog.warn("[Forbric/Lifecycle] could not fire RegisterEvent — mods that register content "
						+ "through DeferredRegister or RegisterEvent will have none of it. The rest of the "
						+ "registration window below still runs", unwrap(t));
			}
			// Fabric mods' onInitialize() calls Registry.register(...) directly, so it belongs in this same unfrozen
			// span. It runs BEFORE the bake below so the bake sees Fabric-registered content. The root registry is
			// opened right here because NewRegistryEvent.fill() above re-froze it: a Fabric mod declaring its own
			// registry goes through FabricRegistryBuilder, which is a plain Registry.register into that root.
			rootRegistry(cl, true);
			try {
				try {
					// On a client these now run from onClientEntrypoints, inside Minecraft.<init>, where Fabric runs
					// them and where Minecraft.getInstance() is live. Here they would see a null instance, and a mod
					// that caches it caches null for the whole process. The dedicated server keeps this window: it has
					// no Minecraft to wait for, and Fabric's own startServer runs main just as early there.
					if (!side.isClient() || !KernelFabricEcosystem.mainsRunInConstructor()) {
						KernelFabricEcosystem.runMainEntrypoints();
					}
				} finally {
					// Fabric's registry freeze, as a server with fabric-api has it: after every main, the root still
					// open. Only the Fabric injectors on BuiltInRegistries.freeze() that FabricFreezeHookMixinAdapter
					// moved run here; the HEAD half now, the TAIL half once the window below is frozen. A client
					// does both in onClientEntrypoints, after its client entrypoints, as Fabric does.
					if (!side.isClient()) fabricFreezePoint(cl, FabricFreezePointInjector.HEAD_HOOK);
				}
			} finally {
				rootRegistry(cl, false);
				// No late-config pass here. It used to sit in this finally, and the comment that justified it said
				// the quiet part: these entrypoints run BEFORE loadEarlyConfigs, "so a config registered here would
				// in fact be caught by it". It was caught by it — TWICE. This pass opened each one, and
				// loadEarlyConfigs then ran ConfigTracker.loadConfigs over the WHOLE type, which re-opens a config
				// that already has one: "Opening a config that was already loaded" per config, ModConfigEvent.Loading
				// delivered a second time (a Loading handler that appends to a list or registers a listener does it
				// twice), the file re-read and a second watcher installed. The late pass now runs AFTER the setup
				// lifecycle instead, where it is the "only what is still unopened" pass it claims to be — see
				// driveNativeRegistration.
			}
			// Bake the ForgeRegistries. Note a DeferredRegister's RegistryObjects bind during their OWN registry's
			// RegisterEvent above (DeferredRegister$EventDispatcher calls updateReference right after each register),
			// not here — so a mod reading another mod's RegistryObject during RegisterEvent depends on the dispatch
			// order, not on this bake.
			// NOT MinecraftForge's GameData.postRegisterEvents, which the kernel called here for years and which
			// NEVER ONCE RAN: its second instruction block is `new LinkedHashSet<>(GameData.vanillaRegistryOrder)`
			// and that field is written only by GameData.vanillaSnapshot(), which the kernel deliberately does not
			// call on this side (it LOCKS the vanilla wrappers — see the NeoForge-only snapshot above). So it threw
			// NPE at instruction 36 on every boot and the warning it produced described the symptom. What it would
			// have reached is the same dispatch loop the kernel already drives itself, plus the attribute events —
			// so the attribute events are what is called, directly, the way NeoForge's tail already is.
			// NeoForge's postRegisterEvents is NOT the bake — it is the dispatch loop the kernel REPLACES: it walks
			// getRegistrationOrder() and re-fires RegisterEvent through ModLoader.postEventWrapContainerInModOrder.
			// While ModList was empty that was a silent no-op, so calling it looked harmless. Once the kernel
			// publishes its mods (KernelModLoader.publishNeoModList) it double-fires every DeferredRegister —
			// "Adding duplicate key 'neoforge:condition_codecs / balm:config'" — and its own error path then calls
			// RegistryManager.revertToVanilla(), ROLLING BACK the NeoForge registries: 21 baseline entries
			// (attribute_type, ticket_type, slot_display, entity_sub_predicate_type, …) silently disappeared.
			// Only its tail is wanted, so call that directly.
			invokeStaticOn(cl, "net.neoforged.neoforge.common.CommonHooks", "modifyAttributes");
			// The rest of postRegisterEvents' tail, in its order. Cheap calls, and each one is a whole feature that
			// simply did not exist: without fireSpawnPlacementEvent a mod's mob has no spawn rules and never
			// generates, without BlockEntityTypeAddBlocksEvent a mod cannot attach its blocks to a vanilla block
			// entity, and without registerModdedCategories its gamerules have no category to sit in.
			// (CreativeModeTabRegistry.sortTabs is the kernel's sortNeoCreativeTabs, below, after the freeze.)
				invokeStaticOn(cl, "net.minecraft.world.entity.SpawnPlacements", "fireSpawnPlacementEvent");
			postModBusEvent(cl, "net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent");
			invokeStaticOn(cl, "net.minecraft.world.level.gamerules.GameRuleCategory", "registerModdedCategories");
			// Last in postRegisterEvents: NeoForge builds its item tooltip appenders — every vanilla component line
			// (enchantments, lore, attributes, durability, …) and every mod's. Left out of this copy of the tail,
			// the merged ItemStack's dispatcher walked three empty lists and tooltips showed only the name.
			if (!"off".equalsIgnoreCase(System.getProperty(NEO_TOOLTIP_APPENDERS, "on"))) {
				invokeStaticOn(cl, "net.forbric.kernel.runtime.KernelNeoTooltips", "init");
			} else {
				ForbricLog.warn("[Forbric/Tooltips] NeoForge's tooltip appenders left unbuilt with -D%s=off — item "
						+ "tooltips show no component lines", NEO_TOOLTIP_APPENDERS);
			}
			ForbricLog.info("[Forbric/Lifecycle] fired RegisterEvent x%d on %d bus(es) [NeoForge baseline + %d mod(s)]",
					n, buses.size(), buses.size() - 1);
			logRegisteredContent(cl);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not register ecosystem content", unwrap(t));
		} finally {
			// Only when the window was actually opened: before unfreeze there is nothing to put back, and freezing
			// a registry the kernel never opened would close one the caller still owns.
			if (closeWindow) closeRegistrationWindow(cl);
			if (!side.isClient()) fabricFreezePoint(cl, FabricFreezePointInjector.TAIL_HOOK);
		}
	}


	/**
	 * Closes the registration window and redoes the bookkeeping the open window invalidated.
	 *
	 * <p>Each of these four is a failure the kernel has already paid for once, so they are named rather than
	 * folded: {@code linkBlockItems} fills {@code Item.BY_BLOCK} (without it {@code Block.asItem()} is AIR and a
	 * mod's creative tab collapses to empty), {@code freeze} also latches {@code registriesLoaded},
	 * {@code rebuildNeoForgeBlockStateIds} re-adds the blockstate ids the open window's clear callback dropped, and
	 * {@code sortNeoCreativeTabs} puts window-registered tabs into the strip the creative screen actually reads.
	 *
	 * <p>Best-effort as a whole AND per step, because this runs in a finally: it must not replace the exception
	 * that brought it here.
	 */
	private static void closeRegistrationWindow(ClassLoader cl) {
		try {
			linkBlockItems(cl);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not link block->item mappings while closing the registration "
					+ "window", unwrap(t));
		}
		try {
			freeze(cl);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not re-freeze the registries — they stay writable for the "
					+ "rest of this run", unwrap(t));
		}
		try {
			rebuildNeoForgeBlockStateIds(cl);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not rebuild the blockstate->id map — the first block update "
					+ "will fail to encode", unwrap(t));
		}
		try {
			sortNeoCreativeTabs(cl);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not re-sort the creative tabs — a mod's tab may be missing "
					+ "from the strip", unwrap(t));
		}
		// After the sort, which is what it reports on.
		if (Boolean.getBoolean("forbric.tabProbe")) startCreativeTabProbe(cl);
	}

	/**
	 * Re-sorts NeoForge's creative-tab ORDER list so tabs registered in the kernel's window become visible.
	 *
	 * <p>The merged {@code CreativeModeInventoryScreen} paginates its tab strip EXCLUSIVELY from
	 * {@code net.neoforged.neoforge.common.CreativeModeTabRegistry.getSortedCreativeModeTabs()} — not from
	 * {@code CreativeModeTabs.tabs()}. That {@code SORTED_TABS} list starts empty and is only rewritten by
	 * {@code sortTabs()}, which walks the whole {@code CREATIVE_MODE_TAB} registry. Vanilla's tabs enter the
	 * registry during {@code Bootstrap} — BEFORE the kernel's registration window — so the sort that ran during
	 * NeoForge baseline bring-up froze a vanilla-only snapshot, and a mod tab registered in the window never
	 * appeared in the strip. It stayed fully SEARCHABLE the whole time, because the search tree is built from
	 * {@code CreativeModeTabs.allTabs()} (the live registry) — that split, "searchable but no tab", is this bug's
	 * fingerprint.
	 *
	 * <p>Calling {@code sortTabs()} again after the window is safe and idempotent: with no server up,
	 * {@code runInServerThreadIfPossible} runs inline; the recalculation is a pure topological sort over the tab
	 * registry plus the ordering-JSON edges; and any later genuine re-sort (the datapack reload listener) walks the
	 * same registry and keeps the tab.
	 */
	private static void sortNeoCreativeTabs(ClassLoader cl) {
		contentCall(cl, "sortCreativeTabs", "re-sort the NeoForge creative tabs");
	}

	/** {@code -Dforbric.tabProbe} — dumps every non-vanilla creative tab's live state every 3s. */
	private static void startCreativeTabProbe(ClassLoader cl) {
		contentCall(cl, "startCreativeTabProbe", "start the creative-tab probe");
	}

	/**
	 * Fills {@code Item.BY_BLOCK} for every registered {@code BlockItem} — the block→item link.
	 *
	 * <p>{@code Block.asItem()} resolves through {@code Item.byBlock(this)}, which is a plain
	 * {@code BY_BLOCK.get(block)}. The merged {@code BlockItem} constructor only stores its block; it never adds
	 * itself to that map. In Forge the map is filled by the ITEMS registry's ADD-CALLBACK
	 * ({@code GameData.ItemCallbacks} -> {@code BlockItem.registerBlocks}), and the kernel registers content without
	 * running those callbacks — so for every modded block {@code asItem()} fell through to AIR.
	 *
	 * <p>That is invisible in the registry dump (the blocks and their items both register fine, and gate-m4 counted
	 * them) but breaks anything that goes block→item. It is why Macaw's Bridges was unreachable: its creative tab
	 * feeds blocks in via {@code Output.accept(ItemLike)}, each became {@code new ItemStack(AIR)} = EMPTY, all ~150
	 * entries were dropped, and Minecraft HIDES a tab that ends up empty — indistinguishable from "the tab was never
	 * registered". Picking a block with the middle mouse button and any recipe/tag lookup that goes through
	 * {@code asItem()} were equally affected.
	 *
	 * <p>{@code putIfAbsent} so an entry vanilla already established always wins; best-effort, because a diagnostic
	 * link-up must never be able to fail the registration window.
	 */
	private static void linkBlockItems(ClassLoader cl) {
		contentCall(cl, "linkBlockItems", "link block->item mappings");
	}

	/**
	 * Reports what the registration window actually put into the vanilla registries, grouped by namespace.
	 *
	 * <p>Constructing a mod is not the same as the mod registering anything, and {@code DeferredRegister} is silent —
	 * so a kernel that fired {@code RegisterEvent} at a mod whose listeners never attached looked exactly like one
	 * that worked. This is the line that tells them apart, and it is how M7 Wall A was confirmed. Best-effort: a
	 * diagnostic must never be able to fail the window it reports on.
	 */
	private static void logRegisteredContent(ClassLoader cl) {
		contentCall(cl, "logRegisteredContent", "summarise registered content");
	}

	/**
	 * Calls one no-arg method on the game-side registry-content class.
	 *
	 * <p>Each of those five already reports its own failure in the terms of what it was repairing, so this only
	 * has to cover the class not being there at all — which on a machine whose boot jar was built without the
	 * staged artifacts is the same message for all five, and {@code KernelRuntimeClasses.verify} has already said
	 * it once at the top of the log.
	 */
	private static void contentCall(ClassLoader cl, String method, String what) {
		try {
			Class.forName("net.forbric.kernel.runtime.KernelRegistryContent", true, cl)
					.getMethod(method).invoke(null);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not " + what, unwrap(t));
		}
	}

	/**
	 * Client-only: construct {@code ClientNeoForgeMod} on the NeoForge baseline bus and route the game's mod-bus
	 * events to it.
	 *
	 * <p>NeoForge's built-in CLIENT registrations — reload listeners ({@code AddClientReloadListenersEvent} adds
	 * {@code AnimationLoader}, {@code ObjLoader}, branding), entity renderers, sprite sources, client extensions —
	 * live in {@code ClientNeoForgeMod}'s {@code @SubscribeEvent} handlers. The merged base's
	 * {@code Minecraft.<init>} fires those events via {@code ClientHooks.initClientHooks →
	 * ModLoader.postEvent(...)}, which iterates {@code ModList.sortedContainers} and calls each container's
	 * {@code acceptEvent} (→ its {@code getEventBus().post(...)}). The kernel seeded an EMPTY ModList, so those
	 * events reached nobody — {@code ModelManager.reload} then NPE'd reading the never-produced
	 * {@code AnimationLoader.STATE_KEY}. Constructing {@code ClientNeoForgeMod} on the baseline bus and pointing the
	 * ModList's one container at that bus makes {@code postEvent} deliver every client mod-bus event to NeoForge's
	 * handlers — the client analogue of the server's native RegisterEvent dispatch.
	 */
	private static void registerNeoForgeClientContent(ClassLoader cl) {
		try {
			Class<?> clientMod = Class.forName("net.neoforged.neoforge.client.ClientNeoForgeMod", false, cl);
			Class<?> iEventBus = Class.forName("net.neoforged.bus.api.IEventBus", false, cl);
			Class<?> modContainer = Class.forName(ForeignType.MOD_CONTAINER.binary(Ecosystem.NEOFORGE), false, cl);
			clientMod.getConstructor(iEventBus, modContainer).newInstance(baselineBus, baselineContainer);
			ForbricLog.info("[Forbric/Lifecycle] constructed ClientNeoForgeMod on the baseline bus");
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not register NeoForge client content — the client's mod-bus "
					+ "events (reload listeners, renderers) will not reach NeoForge", unwrap(t));
		}
	}

	/** {@link #publishModBusDelivery} for both sides; a failure is logged, since the game still runs without it. */
	private static void publishNeoBaselineInModList(ClassLoader cl) {
		try {
			publishModBusDelivery(cl);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not put the NeoForge baseline into ModList — NeoForge's own "
					+ "mod-bus listeners (its payload types among them) will not receive the events NeoForge posts",
					unwrap(t));
		}
	}

	/**
	 * Makes the NeoForge {@code ModList} deliver mod-bus events to the baseline AND to every mod the kernel loaded.
	 *
	 * <p>{@code ModList.sortedContainers} is read by two things that both matter: {@code forEachModInOrder}, which is
	 * how {@code ModLoader.postEvent} fans a mod-bus event out to containers, and {@code getSortedMods()}, which is
	 * what {@code ModListScreen} lists. This method used to set that field (and {@code mods}) to a ONE-element list
	 * holding only the baseline container — which silently undid {@link KernelModLoader#publishNeoModList}, since the
	 * client step runs right after mod construction.
	 *
	 * <p>Both reported symptoms came from that single line. Every game-posted mod-bus event reached only NeoForge's
	 * baseline bus, so a mod's own listeners never fired: AppleSkin registers ALL of its client features with
	 * {@code IEventBus.addListener} on its mod bus ({@code RegisterGuiLayersEvent} for the four HUD overlays,
	 * {@code RegisterClientTooltipComponentFactoriesEvent} for the food tooltip, {@code RegisterPayloadHandlersEvent}
	 * for its sync packets) and got none of them. And the Mods screen listed only the baseline, because it reads the
	 * same field.
	 *
	 * <p>So the list is UNIONed instead of replaced: baseline first (genuine NeoForge also orders it first), then
	 * whatever {@code publishNeoModList} installed. {@code indexedMods} is rebuilt to match so
	 * {@code getModContainerById}/{@code isLoaded} answer for the baseline too.
	 *
	 * <p>Runs on the dedicated server as well, and did not always: it lived inside the client-only step, so every
	 * server's ModList held the mods and not NeoForge. The server posts fewer mod-bus events from game code than the
	 * client, but {@code NetworkRegistry.setup()}'s {@code RegisterPayloadHandlersEvent} is one of them, and its
	 * NeoForge-internal listener is what registers {@code neoforge:recipe_content} and every other built-in payload
	 * type. Without it a server can negotiate a NeoForge connection and then fail to encode the first NeoForge
	 * payload it sends (gate-m12).
	 */
	private static void publishModBusDelivery(ClassLoader cl) throws Exception {
		Class<?> modListCls = Class.forName(ForeignType.MOD_LIST.binary(Ecosystem.NEOFORGE), false, cl);
		Object modList = modListCls.getMethod("get").invoke(null);

		Field modsField = modListCls.getDeclaredField("mods");
		modsField.setAccessible(true);
		Object current = modsField.get(modList);

		List<Object> containers = new ArrayList<>();
		containers.add(baselineContainer);
		if (current instanceof List<?> existing) {
			for (Object c : existing) {
				if (c != null && c != baselineContainer) containers.add(c);
			}
		}

		for (String field : new String[] {"sortedContainers", "mods"}) {
			Field f = modListCls.getDeclaredField(field);
			f.setAccessible(true);
			f.set(modList, List.copyOf(containers));
		}

		try {
			Method getModId = modContainerClass(cl).getMethod("getModId");
			java.util.Map<String, Object> indexed = new java.util.HashMap<>();
			for (Object c : containers) {
				indexed.put((String) getModId.invoke(c), c);
			}
			// "minecraft" goes into the by-id index and NOWHERE else. ModLoadingContext.getActiveContainer()
			// falls back to getModContainerById("minecraft").orElseThrow() when no container is active, and the
			// throw it reaches says "Where is minecraft???!" — so a mod registering an extension point outside a
			// window the kernel wraps got an exception out of NeoForge rather than a container. The container's
			// own getEventBus() returns null by design, which is why it must not join the list the mod-bus
			// fan-out walks.
			indexed.computeIfAbsent("minecraft", id -> minecraftContainerOrNull(cl));
			indexed.values().removeIf(java.util.Objects::isNull);

			Field f = modListCls.getDeclaredField("indexedMods");
			f.setAccessible(true);
			f.set(modList, indexed);
		} catch (Throwable t) {
			ForbricLog.debug("[Forbric/Lifecycle] could not rebuild ModList.indexedMods: %s",
					String.valueOf(unwrap(t)));
		}

		// NeoForge's title-screen version-check overlay (NeoForgeVersionCheck.getStatus →
		// ModList.getModFileById("neoforge").getMods().get(0)) reads the `fileById` map, which our routing does not
		// otherwise touch. It used to be seeded here with the BASELINE'S entry alone, which rendered the main menu
		// and left getModFileById(anyOtherMod) answering null — an NPE inside any mod that resolves its own file by
		// id. The whole container list goes in now, baseline included, through the same helper the publish pass
		// uses, so the two passes cannot disagree about what the map holds.
		try {
			KernelModLoader.publishFileById(cl, modListCls, modList, containers);
		} catch (Throwable t) {
			ForbricLog.debug("[Forbric/Lifecycle] could not seed ModList.fileById (title version-check may NPE): %s",
					String.valueOf(unwrap(t)));
		}
		ForbricLog.info("[Forbric/Lifecycle] NeoForge mod-bus delivery covers %d container(s) — baseline + every "
				+ "loaded mod (ModLoader.postEvent fans out over this list, and the Mods screen lists it)",
				containers.size());
	}

	/**
	 * NeoForge's own {@code "minecraft"} container, or null if it cannot be built.
	 *
	 * <p>Null rather than a throw: failing to publish this costs one fallback lookup, while letting it abort the
	 * index rebuild would cost every mod its container.
	 */
	private static Object minecraftContainerOrNull(ClassLoader cl) {
		try {
			return KernelModContainerFactory.minecraftContainer(cl);
		} catch (Throwable t) {
			ForbricLog.debug("[Forbric/Lifecycle] could not publish the 'minecraft' container: %s",
					String.valueOf(unwrap(t)));
			return null;
		}
	}

	/**
	 * Posts {@code DataPackRegistryEvent.NewRegistry} so mods can declare their own DATAPACK registries.
	 *
	 * <p>Distinct from {@code RegisterEvent}, which the kernel already fires: that one fills registries that exist,
	 * while this one DECLARES per-world registries {@code RegistryDataLoader} must then build from datapacks.
	 * NeoForge accumulates the declarations on the event and flushes them into
	 * {@code DataPackRegistriesHooks.DATA_PACK_REGISTRIES} in its package-private {@code process()}.
	 *
	 * <p>One event instance posted to every bus and processed once — the shape FML uses, and required: the
	 * declarations accumulate ON the event, so a per-mod instance would drop all but the last mod's.
	 *
	 * <p>The baseline bus is included deliberately. NeoForge declares its OWN datapack registries through this same
	 * event ({@code neoforge:biome_modifier}, {@code neoforge:structure_modifier}), so posting it there is what
	 * makes those resolvable — the gap that forced {@code ServerLifecycleHooks.runModifiers} to be neutered.
	 *
	 * <p>Once per process, and never retried: a client reaches it from up to three places (see
	 * {@link DatapackRegistryDeclaration#waitsForFabric}), and a second post would hand every mod's listener the
	 * event twice, while a failed first attempt has usually left a class erroneous that a retry cannot revive.
	 */
	private static void registerDataPackRegistries(ClassLoader cl) {
		if (!DATAPACK_REGISTRIES_DECLARED.compareAndSet(false, true)) return;
		try {
			Class<?> eventCls = Class.forName(
					"net.neoforged.neoforge.registries.DataPackRegistryEvent$NewRegistry", false, cl);
			Class<?> busCls = Class.forName("net.neoforged.bus.api.IEventBus", false, cl);
			Class<?> baseEvent = Class.forName("net.neoforged.bus.api.Event", false, cl);
			Class<?> hooksCls = Class.forName(
					"net.neoforged.neoforge.registries.DataPackRegistriesHooks", false, cl);

			int before = ((java.util.List<?>) hooksCls.getMethod("getDataPackRegistries").invoke(null)).size();

			Object event = eventCls.getConstructor().newInstance();
			Method post = busCls.getMethod("post", baseEvent);

			int posted = 0;
			if (baselineBus != null) {
				post.invoke(baselineBus, event);
				posted++;
			}
			for (java.util.Map.Entry<String, KernelModLoader.NeoIdentity> e
					: KernelModLoader.publishedNeoMods().entrySet()) {
				// The active container matters here too: a mod may resolve itself while building its codec.
				KernelModLoader.setNeoActiveContainer(cl, e.getValue().container());
				try {
					post.invoke(e.getValue().bus(), event);
					posted++;
				} catch (Throwable perMod) {
					ForbricLog.warn("[Forbric/Lifecycle] " + e.getKey()
							+ " failed declaring its datapack registries", unwrap(perMod));
				} finally {
					KernelModLoader.setNeoActiveContainer(cl, null);
				}
			}

			Method process = eventCls.getDeclaredMethod("process");
			process.setAccessible(true);
			process.invoke(event);

			// Name what landed, not just how many: on the merged pack a count alone could not distinguish "the
			// registry a mod needs is present" from "nine OTHER registries are present", and that ambiguity cost a
			// diagnosis. RegistryData is a record whose toString carries the key.
			java.util.List<?> now = (java.util.List<?>) hooksCls.getMethod("getDataPackRegistries").invoke(null);
			java.util.List<String> added = new java.util.ArrayList<>();
			for (int i = before; i < now.size(); i++) added.add(String.valueOf(now.get(i)));
			ForbricLog.info("[Forbric/Lifecycle] posted datapack-registry declaration to %d bus(es) — %d declared "
					+ "(%s), %d total", posted, now.size() - before, added, now.size());

			mirrorIntoFabricDynamicRegistries(cl, now.subList(before, now.size()));
			// Before the Fabric mirror: that one would also carry these across, but as bare (key, codec) copies.
			reconcileLoaderRegistriesIntoNeoForge(cl, hooksCls);
			mirrorFabricDynamicRegistriesIntoNeoForge(cl, eventCls, hooksCls);
			reconcileSynchronizedRegistries(cl, hooksCls);
		} catch (ClassNotFoundException absent) {
			ForbricLog.debug("[Forbric/Lifecycle] no NeoForge DataPackRegistryEvent — skipping");
		} catch (Throwable t) {
			// A class that failed to initialise is a different failure from a declaration that failed, and a far
			// bigger one; it gets a finding, so the player hears it before the title screen and not at "Create".
			CompatibilityFinding poisoned = DatapackRegistryDeclaration.poisonedLoader(t);
			if (poisoned != null) {
				CompatibilityFindings.record(poisoned);
				ForbricLog.error("[Forbric/Lifecycle] " + poisoned.detail(), unwrap(t));
			} else {
				ForbricLog.warn("[Forbric/Lifecycle] could not declare mods' datapack registries — a mod with its own "
						+ "worldgen registry will fail with \"Missing registry\" the moment a world loads", unwrap(t));
			}
		}
	}

	private static final java.util.concurrent.atomic.AtomicBoolean DATAPACK_REGISTRIES_DECLARED =
			new java.util.concurrent.atomic.AtomicBoolean();

	/**
	 * Puts NeoForge's synced datapack registries back into {@code RegistryDataLoader.SYNCHRONIZED_REGISTRIES}, the
	 * list both ends sync from: the server packs each entry of it for the client, and the client builds each one.
	 *
	 * <p>NeoForge's merged {@code <clinit>} makes that field a live view of its own networkable list, and
	 * {@code DataPackRegistryEvent} adds every registry declared with a network codec to it. fabric-api's
	 * {@code DynamicRegistriesImpl.registerSynced} replaces the field with an {@code ArrayList} copy the first time a
	 * Fabric mod syncs a registry of its own, and on a Forbric client the Fabric mains run before NeoForge's
	 * declaration — so every NeoForge mod's synced registry was left out of the copy. The server never sent it, the
	 * client never built it, and the first lookup threw: Create's {@code create:potato_projectile/type} crashed
	 * the client building the creative search tree ("Missing registry"). Each NeoForge entry the list lacks by key
	 * is appended; a list that is still NeoForge's view lacks none. Under {@code -Dforbric.datapackRegistryReconcile}.
	 */
	@SuppressWarnings("unchecked")
	private static void reconcileSynchronizedRegistries(ClassLoader cl, Class<?> hooksCls) {
		try {
			Class<?> loaderCls = Class.forName(DatapackRegistryDeclaration.LOADER, false, cl);
			Class<?> dataCls = Class.forName("net.minecraft.resources.RegistryDataLoader$RegistryData", false, cl);
			Method key = dataCls.getMethod("key");
			Field networkable = hooksCls.getDeclaredField("NETWORKABLE_REGISTRIES");
			networkable.setAccessible(true);
			Field syncedField = loaderCls.getField("SYNCHRONIZED_REGISTRIES");
			java.util.List<Object> synced = (java.util.List<Object>) syncedField.get(null);
			java.util.List<Object> copy = new java.util.ArrayList<>(synced);
			java.util.List<Object> added = DatapackRegistryDeclaration.reconcile((java.util.List<?>) networkable.get(null),
					copy, data -> {
						try {
							return key.invoke(data);
						} catch (ReflectiveOperationException e) {
							throw new IllegalStateException(e);
						}
					}, copy::add, null);
			if (added.isEmpty()) return;
			try {
				synced.addAll(added);
			} catch (UnsupportedOperationException unmodifiable) {
				syncedField.setAccessible(true);
				syncedField.set(null, copy);
			}
			java.util.List<String> keys = new java.util.ArrayList<>();
			for (Object data : added) keys.add(String.valueOf(key.invoke(data)));
			ForbricLog.info("[Forbric/Lifecycle] put %d NeoForge-synced datapack registr(ies) back into "
					+ "RegistryDataLoader.SYNCHRONIZED_REGISTRIES — fabric-api had replaced that live view with a copy "
					+ "before NeoForge's declaration, so the server would not send them and the client would not build "
					+ "them: %s", keys.size(), keys);
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not check NeoForge's synced datapack registries against "
					+ "RegistryDataLoader.SYNCHRONIZED_REGISTRIES — a NeoForge mod's synced registry may be missing on "
					+ "the client", unwrap(t));
		}
	}

	/**
	 * Declares to NeoForge whatever {@code RegistryDataLoader.WORLDGEN_REGISTRIES} ended up holding that NeoForge's
	 * list lacks — the entries a {@code <clinit>} TAIL injector added after NeoForge had already copied the list,
	 * which happens whenever the loader initialises before the hooks. See
	 * {@link DatapackRegistryDeclaration#reconcile} for why the order is not the kernel's to choose.
	 *
	 * <p>Through NeoForge's own {@code addRegistryCodec}, the method {@code NewRegistry.process()} ends in, with the
	 * loader's entry object itself and no network codec — exactly what the hooks-first copy would have put there.
	 * {@code -Dforbric.datapackRegistryReconcile=off} leaves NeoForge's list as it was copied.
	 */
	private static void reconcileLoaderRegistriesIntoNeoForge(ClassLoader cl, Class<?> hooksCls) {
		try {
			Class<?> loaderCls = Class.forName(DatapackRegistryDeclaration.LOADER, false, cl);
			Class<?> dataCls = Class.forName("net.minecraft.resources.RegistryDataLoader$RegistryData", false, cl);
			Class<?> wrapperCls = Class.forName(
					"net.neoforged.neoforge.registries.DataPackRegistryEvent$DataPackRegistryData", false, cl);
			Class<?> codecCls = Class.forName("com.mojang.serialization.Codec", false, cl);
			Method key = dataCls.getMethod("key");
			Constructor<?> wrap = wrapperCls.getDeclaredConstructor(dataCls, codecCls);
			wrap.setAccessible(true);
			Method add = hooksCls.getDeclaredMethod("addRegistryCodec", wrapperCls);
			add.setAccessible(true);
			Field worldgen = loaderCls.getDeclaredField("WORLDGEN_REGISTRIES");
			worldgen.setAccessible(true);

			java.util.List<?> loaderList = (java.util.List<?>) worldgen.get(null);
			java.util.List<?> neoList = (java.util.List<?>) hooksCls.getMethod("getDataPackRegistries").invoke(null);
			java.util.List<Object> replaced = new java.util.ArrayList<>();
			java.util.List<Object> declared = DatapackRegistryDeclaration.reconcile(loaderList, neoList,
					data -> {
						try {
							return key.invoke(data);
						} catch (ReflectiveOperationException e) {
							throw new IllegalStateException(e);
						}
					},
					data -> {
						try {
							add.invoke(null, wrap.newInstance(data, null));
						} catch (ReflectiveOperationException e) {
							throw new IllegalStateException(e);
						}
					}, replaced);
			if (!declared.isEmpty()) {
				java.util.List<String> keys = new java.util.ArrayList<>();
				for (Object data : declared) keys.add(String.valueOf(key.invoke(data)));
				ForbricLog.info("[Forbric/Lifecycle] reconciled %d datapack registr(ies) from RegistryDataLoader's own "
						+ "list into NeoForge's — the loader initialised before DataPackRegistriesHooks, so NeoForge "
						+ "copied the list before a mixin added these, and worlds load only NeoForge's list: %s",
						keys.size(), keys);
			}
			if (!replaced.isEmpty()) {
				ForbricLog.warn("[Forbric/Lifecycle] RegistryDataLoader's list and NeoForge's disagree on the entry for "
						+ "%s — NeoForge's is the one worlds load, so a mixin that replaced it in place is not in effect",
						replaced);
			}
		} catch (Throwable t) {
			ForbricLog.warn("[Forbric/Lifecycle] could not reconcile RegistryDataLoader's list with NeoForge's — a "
					+ "registry a mixin added to the loader may be missing at world load", unwrap(t));
		}
	}

	/**
	 * The same mirror in the other direction: Fabric-declared dynamic registries into NeoForge's list.
	 *
	 * <p><b>Both directions are needed because which list wins is not ours to decide.</b>
	 * {@link #mirrorIntoFabricDynamicRegistries} exists because fabric-api's {@code WorldLoaderMixin} replaces the
	 * loader's argument with Fabric's own list. That mixin stopped applying at NeoForge 26.2.0.88, which widened
	 * {@code RegistryDataLoader.load} from four parameters to five — fabric-api is compiled against vanilla's
	 * four-parameter signature, so its {@code @At(INVOKE)} anchor no longer resolves. Nothing about that is
	 * reported as an error: the mixin simply applies partially, Fabric's substitution never happens, NeoForge's
	 * list is used as-is, and every registry a FABRIC mod declared is missing at world load.
	 *
