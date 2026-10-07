package net.minecraft.world.level.block.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

/**
 * Fixture stand-in with both shapes of the clone query, as the merged base keeps them: vanilla's
 * (level, pos, includeData) and the carrier's (pos, level, includeData, player). The listener calls only the second.
 */
public class BlockState {
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, boolean includeData) {
		return new ItemStack("vanilla-clone pos=" + pos + " level=" + level + " data=" + includeData);
	}

	public ItemStack getCloneItemStack(BlockPos pos, LevelReader level, boolean includeData, Player player) {
		return new ItemStack("clone pos=" + pos + " level=" + level + " data=" + includeData + " player=" + player);
	}
}
