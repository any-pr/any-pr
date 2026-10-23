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

package net.forbric.kernel.runtime;

import java.lang.reflect.Method;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.forbric.api.Ecosystem;
import net.forbric.api.ModCatalog;
import net.forbric.kernel.boot.ModMenuApiStandIn;
import net.forbric.kernel.fabric.ModMenuConfigFactories;
import net.forbric.kernel.util.ForbricLog;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opens a mod's config screen, whichever ecosystem it came from.
 *
 * <h2>Why two answers and not one</h2>
 *
 * <p>There is no cross-loader config screen SPI, and the two that exist do not resemble each other. NeoForge
 * registers an {@code IConfigScreenFactory} as an extension point on the mod's container. Fabric has no config
 * SPI at all — Fabric mods register with <em>Mod Menu</em>, which is itself a mod, so the registry lives in a
 * class this kernel cannot compile against and may not be installed. When it is not, the registrations
 * themselves — each mod's {@code "modmenu"} entrypoint — are still there to be read, and are (see {@link ModMenu}).
 *
 * <p>Each screen therefore answers for its own family and no other. That is why Mod Menu opens Fabric mods'
 * configs and nothing else: it asks its own registry, which only Fabric mods can register in. Nothing is wrong
 * with it. The answer for an instance running both loaders is to ask both registries, which is this class.
 *
 * <h2>Linkage</h2>
 *
 * <p>Each family's lookup is typed, and lives in its own nested class so that it LOADS on its own. A single
 * method naming both families would fail to link on an instance carrying one of them, and the failure would be a
 * {@code NoClassDefFoundError} when a player clicks Config — for a mod of the family that IS present. Mod Menu
 * stays reflective because it is a third-party mod, not a staged artifact.
 */
public final class KernelModConfigScreens {
	private KernelModConfigScreens() {
	}

	/** Mods whose config screen already threw on a press, so the warning is said once and not on every click. */
	private static final java.util.Set<String> PRESS_FAILURES = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Whether {@link #open} would produce a screen. Cheap enough to ask per selection change. */
	public static boolean has(ModCatalog.Entry entry) {
		return entry != null && open(entry, null, true) != null;
	}

	/** The mod's config screen, or {@code null} if its ecosystem has no factory registered for it. */
	public static Screen open(ModCatalog.Entry entry, Screen parent) {
		return open(entry, parent, false);
	}

	/**
	 * @param probe when true, nothing is constructed that would be thrown away — the family is asked only whether
	 *              a factory EXISTS. A probe that built the screen would run a mod's screen constructor on every
	 *              selection change, which is a mod's code running because the player moved the highlight.
	 */
	private static Screen open(ModCatalog.Entry entry, Screen parent, boolean probe) {
		if (entry == null) return null;
		try {
			return switch (entry.ecosystem()) {
				case FABRIC -> ModMenu.resolve(entry.modId(), parent, probe);
				case NEOFORGE -> Neo.resolve(entry.modId(), parent, probe);
				default -> null;
			};
		} catch (Throwable t) {
			// A family that is not present, or a mod whose factory throws. Neither may cost the screen the
			// player is standing in. A probe failing is routine and stays at debug; a press that fails is the player
			// clicking Config and seeing nothing, so it says which mod's factory threw, once per mod.
			if (probe || !PRESS_FAILURES.add(entry.modId())) {
				ForbricLog.debug("[Forbric/ModConfig] no config screen for %s (%s): %s", entry.modId(),
						entry.ecosystem(), String.valueOf(t));
			} else {
				ForbricLog.warn("[Forbric/ModConfig] %s (%s) has a Config button, but its config screen threw when it "
						+ "was opened: %s", entry.modId(), entry.ecosystem(), String.valueOf(t));
			}
			return null;
		}
	}

	/**
	 * How many mods of each ecosystem have a config screen, and the first one that does.
	 *
	 * <p>Exists for the client smoke. The whole claim of this class is that it asks BOTH registries, and the
	 * only way to see that is a count per family: a Fabric-only count would be indistinguishable from Mod Menu's
	 * own answer, which is the thing this replaces.
	 */
	public static String summary() {
		return probe(Ecosystem.FABRIC) + " Fabric, " + probe(Ecosystem.NEOFORGE) + " NeoForge";
	}

	private static int probe(Ecosystem ecosystem) {
		int n = 0;
		for (ModCatalog.Entry entry : ModCatalog.all()) {
			if (entry.ecosystem() == ecosystem && has(entry)) n++;
		}
		return n;
	}

	/** The first mod of {@code ecosystem} with a config screen, or {@code null}. For the smoke, and for it only. */
	public static ModCatalog.Entry firstWithConfig(String ecosystem) {
		for (ModCatalog.Entry entry : ModCatalog.all()) {
			if (entry.ecosystem().name().equalsIgnoreCase(ecosystem) && has(entry)) return entry;
		}
		return null;
	}

	/**
	 * Opens {@code modId}'s config screen over {@code parent}. For the client smoke.
	 *
	 * <p>{@code Object} rather than {@code Screen} in both positions so the boot side can name this method in
	 * {@code KernelRuntimeClasses} — that registry is what turns a renamed game-side method into one line at
	 * startup instead of a {@code NoSuchMethodException} in the middle of a test run.
	 */
	public static Object openById(String modId, Object parent) {
		for (ModCatalog.Entry entry : ModCatalog.all()) {
			if (entry.modId().equals(modId)) return open(entry, (Screen) parent);
		}
		return null;
	}

	/** A stand-in the probe can return: non-null means "yes", and it is never shown. */
	private static final Screen PRESENT = new Screen(net.minecraft.network.chat.Component.empty()) {
	};

	/**
	 * Fabric's answer: Mod Menu's when Mod Menu is installed, and otherwise the mods' own Mod Menu entrypoints.
	 *
	 * <p>Reflective on purpose: Mod Menu is a mod the player may not have installed, and the kernel has no business
	 * requiring one. When it is installed it is asked, exactly as before — it is what a plain Fabric instance asks.
	 *
	 * <p>When it is not, this used to answer "no config" for every Fabric mod, and that was wrong in a way the player
	 * could see: the mods still DECLARE their screens, in a {@code "modmenu"} entrypoint implementing Mod Menu's API.
	 * The kernel stands in for that API ({@code ModMenuApiStandIn}), so those entrypoints link, and
	 * {@link ModMenuConfigFactories} reads them the way Mod Menu's own initializer does. In a player's 40-mod pack with no
	 * Mod Menu, Fabric mods with a Config button went from 0 to 16 of 24, and 15 of the 16 opened their own screen when
 * pressed; Voxy's own factory declined on that machine, which its log said it does where it cannot run.
	 * {@code -Dforbric.modMenuStandIn=off} restores the old answer.
	 */
	private static final class ModMenu {
		private static final String CLASS = "com.terraformersmc.modmenu.ModMenu";
		private static final String API = "com.terraformersmc.modmenu.api.ModMenuApi";
		private static volatile Method has;
		private static volatile Method get;
		/** The mods' own entrypoints, read once; null when Mod Menu is installed or there is nothing to read. */
		private static volatile ModMenuConfigFactories entrypoints;
		private static volatile boolean looked;

		static Screen resolve(String modId, Screen parent, boolean probe) throws Exception {
			if (!looked) look();
			if (has != null) {
				if (!(Boolean) has.invoke(null, modId)) return null;
				return probe ? PRESENT : (Screen) get.invoke(null, modId, parent);
			}
			ModMenuConfigFactories own = entrypoints;
			if (own == null || !own.has(modId)) return null;
			return probe ? PRESENT : (Screen) own.create(modId, parent);
		}

		/** Once: is Mod Menu here, and if not, what do the mods' entrypoints say. {@code looked} is set last. */
		private static synchronized void look() {
			if (looked) return;
			try {
				Class<?> mm = Class.forName(CLASS, false, KernelModConfigScreens.class.getClassLoader());
				has = mm.getMethod("hasConfigScreen", String.class);
				get = mm.getMethod("getConfigScreen", String.class, Screen.class);
				ForbricLog.info("[Forbric/ModConfig] Mod Menu is installed — Fabric mods' config screens come "
						+ "from it, the same place they come from on a plain Fabric instance");
			} catch (Throwable absent) {
				has = null;
				get = null;
				entrypoints = readEntrypoints();
			}
			looked = true;
		}

		private static ModMenuConfigFactories readEntrypoints() {
			String none = "[Forbric/ModConfig] Mod Menu is not installed — Fabric mods will have no config button, "
					+ "because Fabric itself defines no config-screen API for one";
			if (!ModMenuApiStandIn.enabled()) {
				ForbricLog.info(none + " (-D%s=off)", ModMenuApiStandIn.SWITCH);
				return null;
			}
			Class<?> api;
			try {
				api = Class.forName(API, false, KernelModConfigScreens.class.getClassLoader());
			} catch (Throwable absent) {
				// No stand-in offered (a dedicated server, or a kernel built without the game side) and no jar with it.
				ForbricLog.info(none);
				return null;
			}
			try {
				ModMenuConfigFactories read = ModMenuConfigFactories.read(FabricLoader.getInstance(), api);
				int fabric = 0;
				int withConfig = 0;
				for (ModCatalog.Entry entry : ModCatalog.all()) {
					if (entry.ecosystem() != Ecosystem.FABRIC) continue;
					fabric++;
					if (read.has(entry.modId())) withConfig++;
				}
				ForbricLog.info("[Forbric/ModConfig] Mod Menu is not installed — %d of %d Fabric mod(s) get a config "
						+ "button from their own Mod Menu entrypoint, read the way Mod Menu reads it (%d entrypoint(s), "
						+ "%d skipped as broken)", withConfig, fabric, read.entrypoints(), read.broken());
				return read;
			} catch (Throwable t) {
				ForbricLog.warn("[Forbric/ModConfig] could not read Fabric mods' Mod Menu entrypoints (%s) — Fabric "
						+ "mods have no config button", String.valueOf(t));
				return null;
			}
		}
	}

	/** NeoForge's answer: an extension point on the mod's own container, exactly as its own Mods screen reads it. */
	private static final class Neo {
		static Screen resolve(String modId, Screen parent, boolean probe) {
			Optional<? extends net.neoforged.fml.ModContainer> container =
					net.neoforged.fml.ModList.get().getModContainerById(modId);
			if (container.isEmpty()) return null;
			Optional<net.neoforged.neoforge.client.gui.IConfigScreenFactory> factory =
					net.neoforged.neoforge.client.gui.IConfigScreenFactory.getForMod(container.get().getModInfo());
			if (factory.isEmpty()) return null;
			return probe ? PRESENT : factory.get().createScreen(container.get(), parent);
		}
	}
}
