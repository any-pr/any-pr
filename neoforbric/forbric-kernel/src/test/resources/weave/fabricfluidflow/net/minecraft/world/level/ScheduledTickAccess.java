package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluid;

/** Hand-written stand-in, not game code. */
public interface ScheduledTickAccess {
	void scheduleTick(BlockPos pos, Fluid fluid, int delay);
}
