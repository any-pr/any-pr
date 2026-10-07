/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.runtime;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Carries NeoForge's portal hook's complete result back to BaseFireBlock's existing Optional writeback.
 * Its refusal is final: an empty result means no portal spawns.
 *
 * <p>The current unmodified carriers return the input shape or empty. A mod transforming the hook can also
 * return a replacement: this call site has somewhere to carry that result.
 */
public final class KernelPortalSpawn {
	private KernelPortalSpawn() { }

	public static Optional<PortalShape> onTrySpawnPortal(LevelAccessor level, BlockPos position,
			Optional<PortalShape> original) {
		Optional<PortalShape> neo = EventHooks.onTrySpawnPortal(level, position, original);
		return neo == null || neo.isEmpty() ? Optional.empty() : neo;
	}
}
