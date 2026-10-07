package net.fabricmc.fabric.mixin.client.renderer.block.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Synthetic guest mixin in the shape of fabric-renderer-api's chunk hook: before the block walk, a renderer and an
 * emitter into the captured layer map are shared; each block's tessellation is redirected to them.
 */
@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin {
	@Inject(method = "compile", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/core/BlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"))
	private void beforeLoopCompile(SectionPos section, RenderSectionRegion region, VertexSorting sorting, SectionBufferBuilderPack buffers,
			CallbackInfoReturnable<SectionCompiler.Results> info, @Local(name = "startedLayers") Map<String, List<String>> startedLayers,
			@Share("altBlockRenderer") LocalRef<AltModelBlockRenderer> renderer, @Share("altQuadOutput") LocalRef<QuadEmitter> output) {
		renderer.set(Renderer.get().altModelBlockRenderer());
		output.set(Renderer.get().quadEmitter(quad -> startedLayers.computeIfAbsent("solid", layer -> new ArrayList<>()).add(quad)));
	}

	@Redirect(method = "compile", at = @At(value = "INVOKE",
			target = "net/minecraft/client/renderer/block/ModelBlockRenderer.tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
	private void tesselateBlockProxy(ModelBlockRenderer vanilla, BlockQuadOutput vanillaOutput, float x, float y, float z,
			BlockAndTintGetter level, BlockPos pos, BlockState state, BlockStateModel model, long seed,
			@Share("altBlockRenderer") LocalRef<AltModelBlockRenderer> renderer, @Share("altQuadOutput") LocalRef<QuadEmitter> output) {
		renderer.get().tesselateBlock(output.get(), x, y, z, level, pos, state, model, seed);
	}
}
