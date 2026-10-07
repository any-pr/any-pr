package net.minecraft.client.renderer;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hand-written stand-in, not game code: the merged destroy-animation pass collects the breaking block's parts through
 * the context-expanded {@code collectParts}, once, and submits what it collected.
 */
public class LevelRenderer {
	public BlockStateModel breaking = new BlockStateModel();

	public void submitBlockDestroyAnimation(PoseStack poses, SubmitNodeCollector collector, LevelRenderState state) {
		List<Object> parts = new ArrayList<>();
		breaking.collectParts(new BlockAndTintGetter(), new BlockPos(0, 64, 0), new BlockState(), new RandomSource(), parts);
		collector.submitted.addAll(parts);
	}
}
