package net.minecraft.client.renderer.item;

import net.minecraft.world.item.ItemStack;

/** Fixture stand-in: the per-item render state the GUI submits. */
public class TrackingItemStackRenderState {
	private String prepared = "unprepared";

	public void prepare(ItemStack stack, int seed) {
		prepared = stack + "#" + seed;
	}

	@Override
	public String toString() {
		return "state(" + prepared + ")";
	}
}
