package net.fabricmc.fabric.mixin.client.renderer.block.render;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.util.RandomSource;

/**
 * Synthetic guest mixin in the shape of fabric-renderer-api's: vanilla's part collection in the destroy animation is
 * redirected to nothing, because Fabric's renderer emits that geometry itself.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
	@Redirect(method = "submitBlockDestroyAnimation", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;collectParts(Lnet/minecraft/util/RandomSource;Ljava/util/List;)V"))
	private void cancelCollectParts(BlockStateModel model, RandomSource random, List<Object> parts) {
	}
}
