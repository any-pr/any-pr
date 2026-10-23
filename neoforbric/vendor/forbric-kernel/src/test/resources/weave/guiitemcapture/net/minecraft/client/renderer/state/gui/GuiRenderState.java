package net.minecraft.client.renderer.state.gui;

import java.util.ArrayList;
import java.util.List;

/** Fixture stand-in: the frame's submitted items. */
public class GuiRenderState {
	private final List<GuiItemRenderState> items = new ArrayList<>();

	public void addItem(GuiItemRenderState item) {
		items.add(item);
	}

	@Override
	public String toString() {
		return items.toString();
	}
}
