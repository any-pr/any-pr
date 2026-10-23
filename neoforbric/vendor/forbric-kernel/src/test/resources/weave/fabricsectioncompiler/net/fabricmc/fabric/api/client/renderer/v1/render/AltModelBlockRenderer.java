package net.fabricmc.fabric.api.client.renderer.v1.render;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Hand-written stand-in for fabric-renderer-api's block renderer: emits through Fabric's emitter. */
public interface AltModelBlockRenderer {
	void tesselateBlock(QuadEmitter emitter, float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState state,
			BlockStateModel model, long seed);
}
