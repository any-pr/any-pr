package net.fabricmc.fabric.api.entity.event.v1.effect;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Hand-written stand-in for fabric-entity-events' effect events: the two this fixture fires. */
public final class ServerMobEffectEvents {
	public static final Event<BeforeAdd> BEFORE_ADD = new Event<>(listeners -> (effect, entity, context) ->
			listeners.forEach(listener -> listener.beforeAdd(effect, entity, context)));
	public static final Event<AllowEarlyRemove> ALLOW_EARLY_REMOVE = new Event<>(listeners -> (effect, entity, context) ->
			listeners.stream().allMatch(listener -> listener.allowEarlyRemove(effect, entity, context)));

	private ServerMobEffectEvents() {
	}

	public interface BeforeAdd {
		void beforeAdd(MobEffectInstance effect, LivingEntity entity, EffectEventContext context);
	}

	public interface AllowEarlyRemove {
		boolean allowEarlyRemove(MobEffectInstance effect, LivingEntity entity, EffectEventContext context);
	}
}
