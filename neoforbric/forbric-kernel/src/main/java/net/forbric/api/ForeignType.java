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

package net.forbric.api;

/**
 * The NeoForge types the kernel names by string, as ONE row per concept instead of a literal at each call site.
 *
 * <p>NeoForge splits across THREE roots, so there is no prefix rule for turning a concept into a binary name: what
 * descends from FML keeps {@code net.neoforged.} ({@code fml.*}, {@code bus.*}, {@code api.distmarker.*}); the
 * mod-facing game API sits under {@code net.neoforged.neoforge.} ({@code registries.*}, {@code client.*},
 * {@code common.*}, {@code event.*}); and the loader SPI sits under {@code net.neoforged.neoforgespi.}
 * ({@code language.IModInfo}, {@code language.IConfigurable}). Written inline, these names are one typo away from a
 * transform that never fires — a name that does not resolve here does not throw, it just goes nowhere.
 */
public enum ForeignType {
	CLIENT_HOOKS("net.neoforged.neoforge.client.ClientHooks"),
	CLIENT_MOD_LOADER("net.neoforged.neoforge.client.loading.ClientModLoader"),
	CLIENT_COMMANDS_EVENT("net.neoforged.neoforge.client.event.RegisterClientCommandsEvent"),
	CONFIG_TRACKER("net.neoforged.fml.config.ConfigTracker"),
	CONFIGURABLE("net.neoforged.neoforgespi.language.IConfigurable"),
	DIST("net.neoforged.api.distmarker.Dist"),
	EVENT_HOOKS("net.neoforged.neoforge.event.EventHooks"),
	/** The base of every event NeoForge's bus dispatches (EventChainAuditInjector wraps the dispatch). */
	EVENT("net.neoforged.bus.api.Event"),
	FLUID_INTERACTION_REGISTRY("net.neoforged.neoforge.fluids.FluidInteractionRegistry"),
	// The mod-lifecycle phases. The kernel posts each one at every NeoForge container it constructed.
	FML_CONSTRUCT_MOD_EVENT("net.neoforged.fml.event.lifecycle.FMLConstructModEvent"),
	FML_CLIENT_SETUP_EVENT("net.neoforged.fml.event.lifecycle.FMLClientSetupEvent"),
	FML_COMMON_SETUP_EVENT("net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent"),
	FML_DEDICATED_SERVER_SETUP_EVENT("net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent"),
	FML_LOAD_COMPLETE_EVENT("net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent"),
	/** NeoForge's environment answers are stateless — two static methods and no fields. */
	FML_ENVIRONMENT("net.neoforged.fml.loading.FMLEnvironment"),
	FML_LOADER("net.neoforged.fml.loading.FMLLoader"),
	FML_PATHS("net.neoforged.fml.loading.FMLPaths"),
	GAME_DATA("net.neoforged.neoforge.registries.GameData"),
	/**
	 * NeoForge's own built-in translations — the table its screens read before any resource pack exists. The
	 * client mod loader the kernel replaces is what calls its loader, so a name that goes nowhere is a screen
	 * that quietly renders raw keys.
	 */
	LANGUAGE_HOOK("net.neoforged.neoforge.server.LanguageHook"),
	LOADING_MOD_LIST("net.neoforged.fml.loading.LoadingModList"),
	INTER_MOD_ENQUEUE_EVENT("net.neoforged.fml.event.lifecycle.InterModEnqueueEvent"),
	INTER_MOD_PROCESS_EVENT("net.neoforged.fml.event.lifecycle.InterModProcessEvent"),
	MOD_BUS_EVENT("net.neoforged.fml.event.IModBusEvent"),
	MOD_CONTAINER("net.neoforged.fml.ModContainer"),
	MOD_FILE("net.neoforged.fml.loading.moddiscovery.ModFile"),
	MOD_FILE_TYPE("net.neoforged.neoforgespi.locating.IModFile$Type"),
	/** NeoForge's annotation index for one mod file; a file the kernel seeds must answer {@code getScanResult()}. */
	MOD_FILE_SCAN_DATA("net.neoforged.neoforgespi.language.ModFileScanData"),
	/** NeoForge's rewriter for enums a mod may add constants to. */
	RUNTIME_ENUM_EXTENDER("net.neoforged.fml.common.asm.enumextension.RuntimeEnumExtender"),
	MOD_FILE_INFO("net.neoforged.fml.loading.moddiscovery.ModFileInfo"),
	MOD_INFO("net.neoforged.fml.loading.moddiscovery.ModInfo"),
	MOD_INFO_SPI("net.neoforged.neoforgespi.language.IModInfo"),
	MOD_LIST("net.neoforged.fml.ModList"),
	MOD_LOADING_CONTEXT("net.neoforged.fml.ModLoadingContext"),
	MOD_LIST_SCREEN("net.neoforged.neoforge.client.gui.modlist.ModListScreen"),
	NEW_REGISTRY_EVENT("net.neoforged.neoforge.registries.NewRegistryEvent"),
	/**
	 * NeoForge's resource-condition type: a separate registry with its own dialect, running over every element
	 * from every pack, so a condition the kernel cannot resolve must not fail the whole registry load.
	 */
	ICONDITION("net.neoforged.neoforge.common.conditions.ICondition"),
	NETWORK_REGISTRY("net.neoforged.neoforge.network.registration.NetworkRegistry"),
	REGISTRY_MANAGER("net.neoforged.neoforge.registries.RegistryManager"),
	SERVER_LIFECYCLE_HOOKS("net.neoforged.neoforge.server.ServerLifecycleHooks"),
	SERVER_MOD_LOADER("net.neoforged.neoforge.server.loading.ServerModLoader"),
	/** The static hook class NeoForge's patched game calls to post its events. */
	EVENT_FACTORY("net.neoforged.neoforge.event.EventHooks"),
	/** NeoForge's fluid type: a fluid without one gets the type its tags imply (ForeignFluidTypeInjector). */
	FLUID_TYPE("net.neoforged.neoforge.fluids.FluidType"),
	/** NeoForge's multipart-entity part: the Ender Dragon's parts are this (ClientPartTrackingInjector). */
	PART_ENTITY("net.neoforged.neoforge.entity.PartEntity"),
	/** NeoForge's per-tab creative search keys, which also hold the trees (CreativeSearchTreesInjector). */
	CREATIVE_SEARCH_REGISTRY("net.neoforged.neoforge.client.CreativeModeTabSearchRegistry"),
	/** NeoForge's global-loot-modifier reload listener. */
	LOOT_MODIFIER_MANAGER("net.neoforged.neoforge.common.loot.LootModifierManager");

	private final String neoforge;

	ForeignType(String neoforge) {
		this.neoforge = neoforge;
	}

	/** The binary (dotted) name in {@code ecosystem}, or {@code null} for {@link Ecosystem#FABRIC}. */
	public String binary(Ecosystem ecosystem) {
		return switch (ecosystem) {
			case NEOFORGE -> neoforge;
			case FABRIC -> null;
		};
	}

	/** The internal (slash) name in {@code ecosystem}, for ASM. */
	public String internal(Ecosystem ecosystem) {
		String binary = binary(ecosystem);
		return binary == null ? null : binary.replace('.', '/');
	}

	/** Whether {@code binaryName} is this concept in the Forge-family ecosystem. */
	public boolean matches(String binaryName) {
		return neoforge.equals(binaryName);
	}
}
