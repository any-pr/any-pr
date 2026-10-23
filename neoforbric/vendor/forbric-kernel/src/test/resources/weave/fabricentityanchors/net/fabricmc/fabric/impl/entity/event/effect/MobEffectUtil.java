package net.fabricmc.fabric.impl.entity.event.effect;

import net.fabricmc.fabric.api.entity.event.v1.effect.EffectEventContext;

/** Hand-written stand-in for fabric-entity-events' helper. */
public final class MobEffectUtil {
	private MobEffectUtil() {
	}

	public static EffectEventContext getCommandContext() {
		return new EffectEventContext();
	}
}
