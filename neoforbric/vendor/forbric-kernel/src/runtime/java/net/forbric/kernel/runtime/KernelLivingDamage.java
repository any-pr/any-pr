/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.runtime;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

/**
 * Keeps the hit's {@code DamageContainer} truthful about a rewritten {@code actuallyHurt} damage.
 */
public final class KernelLivingDamage {
	private KernelLivingDamage() {
	}

	/**
	 * Vanilla's first read of {@code actuallyHurt}'s damage, which a mod may have rewritten ({@code read}), against the
	 * parameter as it came ({@code param}); VanillaDamageReadInjector places it before armour. A rewrite moves the
	 * container by the same amount; no rewrite changes nothing, so NeoForge's own amount stays exactly as it was.
	 */
	public static void vanillaRead(LivingEntity entity, DamageContainer container, float read, float param) {
		if (container == null || Float.floatToIntBits(read) == Float.floatToIntBits(param)) return;
		container.setNewDamage(Math.max(0.0F, container.getNewDamage() + (read - param)));
	}
}
