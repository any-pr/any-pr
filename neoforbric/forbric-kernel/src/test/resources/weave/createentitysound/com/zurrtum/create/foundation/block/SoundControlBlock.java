package com.zurrtum.create.foundation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;

/** The mod's own say on which sound group a block of it makes, where it stands. */
public interface SoundControlBlock {
	SoundType getSoundGroup(LevelReader level, BlockPos pos);
}
