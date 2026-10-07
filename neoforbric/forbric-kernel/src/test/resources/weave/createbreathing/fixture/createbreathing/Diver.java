package fixture.createbreathing;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** An entity wearing the mod's diving gear. */
public class Diver extends LivingEntity {
	public Diver(Level level, String fluid) {
		super(level, fluid);
	}
}
