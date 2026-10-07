package net.minecraft.world.level.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Fixture stand-in: every position starts as air. */
public class LevelChunk {
	public BlockState setBlockState(BlockPos pos, BlockState state) {
		return new BlockState(new Block("minecraft:air"));
	}
}
