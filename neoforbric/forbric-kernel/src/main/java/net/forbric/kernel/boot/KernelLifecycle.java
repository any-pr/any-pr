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
