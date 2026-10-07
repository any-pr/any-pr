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

package com.terraformersmc.modmenu.api;

import java.util.Map;
import java.util.function.Consumer;

import com.terraformersmc.modmenu.util.NullScreenFactory;

import net.forbric.kernel.runtime.KernelModListScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Forbric's stand-in for Mod Menu's {@code ModMenuApi}, present only when no installed jar has the real one.
 *
 * <h2>Why the kernel carries this at all</h2>
 *
 * <p>Fabric defines no config-screen API. A Fabric mod with a settings screen says so in exactly one place: a
 * {@code "modmenu"} entrypoint whose class implements this interface. Without Mod Menu installed, that class cannot
 * even be linked — its interface does not exist — so the declaration is unreadable and the mod's config is
 * unreachable from any Mods screen, including the kernel's. A player's 40-mod pack had 17 Fabric mods declaring one
 * and no Mod Menu, and every one of them showed no Config button.
 *
 * <p>With this interface present, those entrypoints link, and {@code KernelModConfigScreens} reads them the way Mod
 * Menu's own client initializer does. The mod {@code modmenu} itself stays absent —
 * {@code FabricLoader.isModLoaded("modmenu")} is still false — so a mod that guards Mod-Menu-only code (a mixin into
 * Mod Menu's own screen, a call into its internals) keeps skipping it, as it does on any instance without Mod Menu.
 *
 * <h2>Shape</h2>
 *
 * <p>Every member matches Mod Menu 20.0.3 for Minecraft 26.2, by name, descriptor, static-ness and default-ness,
 * because mods are compiled against that one and link against whatever is here. {@code ModMenuApiStandInShapeTest}
 * pins it. Only the BODIES differ, and only where Mod Menu's reach into its own internals: the mods screen is the
 * kernel's unified list, and the default factory produces no screen, as Mod Menu's {@code NullScreenFactory} does.
 */
public interface ModMenuApi {
	/** The kernel's unified Mods screen: it is the list on this instance, and Mod Menu's own is not installed. */
	static Screen createModsScreen(Screen previous) {
		return KernelModListScreen.create(previous);
	}

	/** The label for a button that opens {@link #createModsScreen}, worded as that screen titles itself. */
	static Component createModsButtonText() {
		return Component.literal("Mods");
	}

	/**
	 * This mod's config screen factory. The default is a {@link NullScreenFactory}, as Mod Menu's is: the marker a
	 * reader skips, so a mod that does not override this — or overrides it and falls back to this default — has no
	 * Config button.
	 */
	default ConfigScreenFactory<?> getModConfigScreenFactory() {
		return new NullScreenFactory<>();
	}

	/** Mod Menu checks for updates with this. The kernel does not check for updates, so nothing calls it. */
	default UpdateChecker getUpdateChecker() {
		return null;
	}

	/** Config screen factories this mod provides for OTHER mods, by their mod id — a config library's way in. */
	default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
		return Map.of();
	}

	/** Update checkers this mod provides for other mods. Never read, for the same reason as {@link #getUpdateChecker}. */
	default Map<String, UpdateChecker> getProvidedUpdateCheckers() {
		return Map.of();
	}

	/** Mod Menu's modpack badges. The kernel's list draws none, so nothing calls it. */
	default void attachModpackBadges(Consumer<String> consumer) {
	}
}
