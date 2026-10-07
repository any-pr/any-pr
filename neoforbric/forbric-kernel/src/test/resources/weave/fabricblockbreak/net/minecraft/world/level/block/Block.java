package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** Hand-written stand-in, not game code: a block whose break hands back an adjusted state. */
public class Block {
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		return new BlockState("adjusted-" + state.name(), this, state.removable());
	}

	public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
	}
}
