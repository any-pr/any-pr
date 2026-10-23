package net.fabricmc.fabric.mixin.client.model.loading;

import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.impl.client.model.loading.CustomUnbakedBlockStateModelRegistry;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;

/**
 * Synthetic guest mixin in the shape of fabric-model-loading's: an interface mixin whose two static redirects replace
 * the codec each {@code flatComapMap} in the static initialiser builds with Fabric's own.
 */
@Mixin(BlockStateModel.Unbaked.class)
interface BlockStateModelUnbakedMixin {
	@Redirect(method = "<clinit>()V", at = @At(value = "INVOKE",
			target = "Lcom/mojang/serialization/Codec;flatComapMap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;",
			ordinal = 0))
	private static Codec<?> replaceWeightedCodec(Codec<?> codec, Function<?, ?> to, Function<?, ?> from) {
		return CustomUnbakedBlockStateModelRegistry.WEIGHTED_MODEL_CODEC;
	}

	@Redirect(method = "<clinit>()V", at = @At(value = "INVOKE",
			target = "Lcom/mojang/serialization/Codec;flatComapMap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;",
			ordinal = 1))
	private static Codec<?> replaceCodec(Codec<?> codec, Function<?, ?> to, Function<?, ?> from) {
		return CustomUnbakedBlockStateModelRegistry.MODEL_CODEC;
	}
}
