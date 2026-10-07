/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.runtime;

import java.lang.reflect.Field;

import com.google.common.collect.Table;
import net.forbric.kernel.util.ForbricLog;
import net.forbric.kernel.util.Reflect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.neoforged.neoforge.registries.GameData;

/**
 * Which full pot a plant makes (FlowerPotRepairInjector).
 *
 * <p>NeoForge declares a pot by its constructor (empty pot + plant) and keeps them in {@code GameData}'s pot table,
 * which its registry bake callback fills — a bake that never runs under the kernel's own registry handling, so
 * {@link #rebuildTable} fills it from every registered pot the way NeoForge's bake does. A vanilla or Fabric pot is
 * in that table too: NeoForge's (Block, Properties) constructor names the vanilla empty pot.
 */
public final class KernelFlowerPots {
	private static volatile boolean warned;

	private KernelFlowerPots() {
	}

	/** The full pot {@code content} makes in {@code self}'s empty pot, or air. Never throws. */
	public static Block fullPotFor(FlowerPotBlock self, Block content) {
		try {
			Block full = GameData.getFlowerPotBlockTable().get(self.getEmptyPot(), content);
			return full != null ? full : Blocks.AIR;
		} catch (Throwable failure) {
			if (!warned) {
				warned = true;
				ForbricLog.warn("[Forbric/FlowerPot] could not look up the full pot for %s (%s); the pot stays empty",
						content, Reflect.unwrap(failure));
			}
			return Blocks.AIR;
		}
	}

	/**
	 * Fills NeoForge's pot table from every registered pot, as its bake callback would: each pot that is not its own
	 * empty pot is the full pot of (its empty pot, its plant). Returns how many pots were entered, or -1 on failure.
	 */
	public static int rebuildTable() {
		// The same switches as the rest of the flower pot repair (NativeCoremodParity, FlowerPotRepairInjector).
		if ("off".equalsIgnoreCase(System.getProperty("forbric.coremodParity", "on"))
				|| "off".equalsIgnoreCase(System.getProperty("forbric.flowerPotRepair", "on"))) return -1;
		try {
			Table<Block, Block, Block> table = GameData.getFlowerPotBlockTable();
			table.clear();
			int entered = 0, failed = 0;
			for (Block block : BuiltInRegistries.BLOCK) {
				if (!(block instanceof FlowerPotBlock pot)) continue;
				try {
					FlowerPotBlock empty = pot.getEmptyPot();
					if (empty == pot) continue;
					table.put(empty, pot.getPotted(), pot);
					entered++;
				} catch (Throwable unresolved) {
					failed++;   // one mod's unbound supplier must not cost every other pot its entry
				}
			}
			invalidateLegacyView();
			ForbricLog.info("[Forbric/FlowerPot] filled NeoForge's flower pot table with %d pot(s)%s — its bake callback "
					+ "never runs under the kernel's registry handling, so every plant would look up air", entered,
					failed == 0 ? "" : " (" + failed + " pot(s) could not name their plant yet)");
			return entered;
		} catch (Throwable failure) {
			ForbricLog.warn("[Forbric/FlowerPot] could not fill NeoForge's flower pot table", Reflect.unwrap(failure));
			return -1;
		}
	}

	/** The (empty pot, id) → supplier view NeoForge derives lazily from the table; drop it so it re-derives. */
	private static void invalidateLegacyView() {
		try {
			Class<?> callbacks = Class.forName("net.neoforged.neoforge.registries.NeoForgeRegistryCallbacks$BlockCallbacks");
			Field lazy = callbacks.getDeclaredField("LEGACY_EMPTY_POT_AND_FLOWER_TO_FULL_POT_TABLE");
			lazy.setAccessible(true);
			Object value = lazy.get(null);
			value.getClass().getMethod("invalidate").invoke(value);
		} catch (Throwable absent) {
			ForbricLog.debug("[Forbric/FlowerPot] NeoForge's legacy pot view could not be invalidated: %s", absent);
		}
	}
}
