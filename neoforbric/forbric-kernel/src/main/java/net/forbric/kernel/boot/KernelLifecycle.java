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
