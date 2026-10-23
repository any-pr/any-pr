package net.minecraft.client.renderer.state.gui;

import net.minecraft.client.renderer.item.TrackingItemStackRenderState;

/** Fixture stand-in: one submitted GUI item. */
public record GuiItemRenderState(TrackingItemStackRenderState state, int x, int y) {
	@Override
	public String toString() {
		return state + "@" + x + "," + y;
	}
}
