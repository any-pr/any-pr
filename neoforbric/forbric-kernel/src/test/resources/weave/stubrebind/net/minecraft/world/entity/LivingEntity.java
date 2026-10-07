package net.minecraft.world.entity;

import net.minecraft.world.item.ItemStack;

/**
 * Hand-written stand-in with the SHAPE of one carrier-stubs.txt row, no game code: a carrier widened
 * randomTeleport(DDDZ)Z with an ItemStack and kept vanilla's signature, declared first, as a stub that only forwards.
 * The overload carries the body, and it is what the merged game calls.
 */
public class LivingEntity {
	public final StringBuilder trace = new StringBuilder();

	public boolean randomTeleport(double x, double y, double z, boolean broadcast) {
		return randomTeleport(x, y, z, broadcast, ItemStack.EMPTY);
	}

	public boolean randomTeleport(double x, double y, double z, boolean broadcast, ItemStack item) {
		trace.append("body;");
		return land(x, y, z);
	}

	public boolean land(double x, double y, double z) {
		trace.append("land;");
		return y > 0;
	}
}
