package com.zurrtum.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fixture.createbreathing.DivingHelmet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Shaped like Create Fly's LivingEntityMixin breathing hooks, written against vanilla's baseTick: one wraps the
 * isEyeInFluid(WATER) check to let the diving helmet act in lava, the other wraps MobEffectUtil.hasWaterBreathing so
 * the backtank supplies air under water. Neither reads {@code this}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@WrapOperation(method = "baseTick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean breatheInLava(LivingEntity entity, TagKey<Fluid> tagKey, Operation<Boolean> original, @Local ServerLevel level) {
		if (original.call(entity, tagKey)) {
			return true;
		}
		if (entity.isInLava()) {
			DivingHelmet.breatheInLava(entity, level);
		}
		return false;
	}

	@WrapOperation(method = "baseTick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffectUtil;hasWaterBreathing(Lnet/minecraft/world/entity/LivingEntity;)Z"))
	private boolean canBreatheInWater(LivingEntity mob, Operation<Boolean> original, @Local ServerLevel level) {
		if (original.call(mob)) {
			return true;
		}
		return DivingHelmet.breatheUnderwater(mob, level);
	}
}
