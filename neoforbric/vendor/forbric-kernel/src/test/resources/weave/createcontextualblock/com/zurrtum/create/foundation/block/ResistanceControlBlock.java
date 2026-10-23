package com.zurrtum.create.foundation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/** The mod's own say on how much an explosion a block of it resists, where it stands. */
public interface ResistanceControlBlock {
	float getResistance(BlockGetter level, BlockPos pos);
}
