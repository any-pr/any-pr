package com.zurrtum.create.foundation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.state.BlockState;

/** The mod's own say on whether one of its blocks passes on weak power. */
public interface WeakPowerControlBlock {
	boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction side);
}
