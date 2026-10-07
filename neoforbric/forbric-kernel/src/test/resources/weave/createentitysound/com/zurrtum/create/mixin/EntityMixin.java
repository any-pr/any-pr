package com.zurrtum.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.foundation.block.SoundControlBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Shaped like Create Fly's EntityMixin step-sound hook, written against vanilla's Entity.playStepSound: it wraps the
 * state.getSoundType() call, so a block of the mod picks its sound group from the entity's level and the step's
 * position.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Shadow private Level level;

	@WrapOperation(method = "playStepSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
	private SoundType getStepSound(BlockState state, Operation<SoundType> original, @Local(argsOnly = true) BlockPos pos) {
		Block block = state.getBlock();
		if (block instanceof SoundControlBlock control) {
			return control.getSoundGroup(this.level, pos);
		}
		return original.call(state);
	}
}
