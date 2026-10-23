package net.minecraft.world.level.block.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;

/** Hand-written stand-in, not game code. */
public record BlockState(String name, Block getBlock, boolean removable) {
	public boolean canHarvestBlock(BlockGetter level, BlockPos pos, Player player) {
		return true;
	}
}
