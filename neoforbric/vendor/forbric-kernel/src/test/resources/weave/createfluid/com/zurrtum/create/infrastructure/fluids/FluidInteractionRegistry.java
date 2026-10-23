package com.zurrtum.create.infrastructure.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** The mod's own registry: it reacts the fluid where the level says one of its fluids meets another. */
public final class FluidInteractionRegistry {
	private FluidInteractionRegistry() {
	}

	public static boolean canInteract(Level level, BlockPos pos) {
		if (!level.reactsAt(pos)) return false;
		level.trail.add("create reacted " + pos.name());
		return true;
	}
}
