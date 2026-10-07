package net.minecraft.client.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Hand-written stand-in, not game code. */
public class Screen {
	private final String name;

	public Screen(String name) {
		this.name = name;
	}

	public String name() {
		return name;
	}

	public void extractRenderStateWithTooltipAndSubtitles(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.draws.add("screen:" + name);
	}
}
