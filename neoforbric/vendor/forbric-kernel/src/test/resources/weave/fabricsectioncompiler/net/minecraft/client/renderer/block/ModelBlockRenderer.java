package net.minecraft.client.renderer.block;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Hand-written stand-in, not game code: vanilla's block tessellation, one quad per block. */
public class ModelBlockRenderer {
	public void tesselateBlock(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockPos pos,
			BlockState state, BlockStateModel model, long seed) {
		output.put("vanilla:" + model.name());
	}
}
