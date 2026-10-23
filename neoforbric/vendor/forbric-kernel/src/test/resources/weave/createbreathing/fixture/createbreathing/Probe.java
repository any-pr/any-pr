package fixture.createbreathing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * A diver ticks three times under water, then once in lava: the air it has left, and what the mod's gear did, in order.
 */
public class Probe {
	public String probe() {
		ServerLevel level = new ServerLevel();
		LivingEntity underwater = new Diver(level, "water");
		for (int tick = 0; tick < 3; tick++) underwater.baseTick();
		LivingEntity inLava = new Diver(level, "lava");
		inLava.baseTick();
		return "air " + underwater.getAirSupply() + " | gear " + (level.events.isEmpty() ? "idle" : String.join(", ", level.events));
	}
}
