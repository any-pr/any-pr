package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;

/** A stand-in: no entity here has a water-breathing effect. */
public final class MobEffectUtil {
	private MobEffectUtil() {
	}

	public static boolean hasWaterBreathing(LivingEntity entity) {
		return false;
	}
}
