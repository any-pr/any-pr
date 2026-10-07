package com.zurrtum.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.foundation.block.ResistanceControlBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Shaped like Create Fly's ExplosionDamageCalculatorMixin, written against vanilla: it wraps the block's
 * getExplosionResistance() call, so a block of the mod answers for itself with the level and position the calculator was
 * handed.
 */
@Mixin(ExplosionDamageCalculator.class)
public class ExplosionDamageCalculatorMixin {
	@WrapOperation(method = "getBlockExplosionResistance(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)Ljava/util/Optional;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getExplosionResistance()F"))
	private float getBlastResistance(Block block, Operation<Float> original, @Local(argsOnly = true) BlockGetter level,
			@Local(argsOnly = true) BlockPos pos) {
		if (block instanceof ResistanceControlBlock controlBlock) {
			return controlBlock.getResistance(level, pos);
		}
		return original.call(block);
	}
}
