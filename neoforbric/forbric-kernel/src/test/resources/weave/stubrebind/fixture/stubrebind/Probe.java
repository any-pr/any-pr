package fixture.stubrebind;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Calls the body the way the merged game does, and the stub the way only old callers do; returns both traces. */
public class Probe {
	public String run() {
		LivingEntity viaBody = new LivingEntity();
		viaBody.randomTeleport(1, 2, 3, true, ItemStack.EMPTY);
		LivingEntity viaStub = new LivingEntity();
		viaStub.randomTeleport(4, 5, 6, false);
		return "body[" + viaBody.trace + "] stub[" + viaStub.trace + "]";
	}
}
