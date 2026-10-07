package net.minecraft.client.renderer.chunk;

import java.util.List;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Hand-written stand-in, not game code: a row of blocks to compile. */
public class RenderSectionRegion implements BlockAndTintGetter {
	private final List<String> blocks;

	public RenderSectionRegion(List<String> blocks) {
		this.blocks = blocks;
	}

	public BlockPos first() {
		return new BlockPos(0);
	}

	public BlockPos last() {
		return new BlockPos(blocks.size() - 1);
	}

	public BlockState getBlockState(BlockPos pos) {
		return new BlockState(new BlockStateModel(blocks.get(pos.x())));
	}
}
