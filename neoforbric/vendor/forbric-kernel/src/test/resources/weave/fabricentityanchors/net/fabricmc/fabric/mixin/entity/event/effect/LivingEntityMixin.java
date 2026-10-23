package net.fabricmc.fabric.mixin.entity.event.effect;

import java.util.Map;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.fabricmc.fabric.impl.entity.event.effect.MobEffectUtil;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Synthetic guest mixin in the shape of fabric-entity-events': BEFORE_ADD right after vanilla's {@code canBeAffected}
 * in {@code forceAddEffect}, and a wrap of vanilla's {@code activeEffects.clear()} that puts back what
 * ALLOW_EARLY_REMOVE vetoes.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin extends Entity {
	private LivingEntityMixin(Level level) {
		super(level);
	}

	@Inject(method = "forceAddEffect", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;canBeAffected(Lnet/minecraft/world/effect/MobEffectInstance;)Z",
			shift = At.Shift.AFTER))
	private void beforeForceAddEffect(MobEffectInstance effect, Entity source, CallbackInfo info) {
		if (isClient()) return;
		ServerMobEffectEvents.BEFORE_ADD.invoker().beforeAdd(effect, self(), MobEffectUtil.getCommandContext());
	}

	@WrapOperation(method = "removeAllEffects", at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V"))
	private void allowRemoveAllEffects(Map<Holder<MobEffect>, MobEffectInstance> effects, Operation<Void> original) {
		if (isClient()) return;
		Set<Map.Entry<Holder<MobEffect>, MobEffectInstance>> before = Set.copyOf(effects.entrySet());
		original.call(effects);
		for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : before) {
			boolean vetoed = !ServerMobEffectEvents.ALLOW_EARLY_REMOVE.invoker().allowEarlyRemove(entry.getValue(), self(),
					MobEffectUtil.getCommandContext());
			if (vetoed) effects.put(entry.getKey(), entry.getValue());
		}
	}

	@Unique
	private boolean isClient() {
		return level().isClientSide();
	}

	@Unique
	private LivingEntity self() {
		return (LivingEntity) (Object) this;
	}
}
