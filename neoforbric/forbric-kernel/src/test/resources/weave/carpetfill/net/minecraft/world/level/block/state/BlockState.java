package net.minecraft.world.level.block.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;

/** Fixture stand-in: a block's state, which tells the level when it updates its neighbours' shapes. */
public record BlockState(Block block) {
	public Block getBlock() {
		return block;
	}

	public void updateNeighbourShapes(LevelAccessor level, BlockPos pos, int flags, int updateLimit) {
		level.record("shapes@" + pos);
	}
}
