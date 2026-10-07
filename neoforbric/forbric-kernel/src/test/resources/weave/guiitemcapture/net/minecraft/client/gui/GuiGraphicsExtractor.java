package net.minecraft.client.gui;

import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Fixture stand-in for the merged GUI extractor: item builds its render state and submits it inside the non-empty
 * branch, so that local is dead again at the join the method's tail sits on.
 */
public class GuiGraphicsExtractor {
	private final GuiRenderState guiRenderState = new GuiRenderState();
	private String tooltip = "none";

	public void item(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed) {
		if (!stack.isEmpty()) {
			TrackingItemStackRenderState state = new TrackingItemStackRenderState();
			state.prepare(stack, seed);
			guiRenderState.addItem(new GuiItemRenderState(state, x, y));
		}
	}

	public void setTooltipForNextFrame(Component text, int x, int y) {
		tooltip = text + "@" + x + "," + y;
	}

	public String submitted() {
		return guiRenderState + " tooltip=" + tooltip;
	}
}
