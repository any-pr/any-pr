package fixture.createbreathing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** The mod's diving gear: a backtank that supplies air under water, a helmet that protects in lava. */
public final class DivingHelmet {
	private DivingHelmet() {
	}

	public static boolean breatheUnderwater(LivingEntity entity, ServerLevel level) {
		if (!(entity instanceof Diver)) return false;
		level.events.add("backtank");
		return true;
	}

	public static void breatheInLava(LivingEntity entity, ServerLevel level) {
		if (entity instanceof Diver) level.events.add("lava helmet");
	}
}
