package net.minecraft.client.renderer.block.dispatch;

import java.util.List;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Hand-written stand-in, not game code: the merged model collects its parts with the block's context. */
public class BlockStateModel {
	public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<Object> parts) {
		parts.add("vanilla-part");
	}
}
