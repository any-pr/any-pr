package fixture.fabricentityanchors;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Adds three effects on a server-side entity, then clears them: a Fabric listener vetoes removing one, NeoForge's own
 * listeners keep another. Reports what BEFORE_ADD saw and what is left.
 */
public class Probe {
	public String run() {
		List<String> announced = new ArrayList<>();
		ServerMobEffectEvents.BEFORE_ADD.register((effect, entity, context) -> announced.add(effect.name()));
		ServerMobEffectEvents.ALLOW_EARLY_REMOVE.register((effect, entity, context) -> !effect.name().equals("fabric_kept"));

		LivingEntity entity = new LivingEntity(new Level(false));
		for (String name : List.of("speed", "fabric_kept", "neo_kept")) {
			entity.forceAddEffect(new MobEffectInstance(new Holder<>(new MobEffect(name))), null);
		}
		boolean removed = entity.removeAllEffects();
		return "announced=" + announced + " removed=" + removed + " left=" + entity.effectNames();
	}
}
