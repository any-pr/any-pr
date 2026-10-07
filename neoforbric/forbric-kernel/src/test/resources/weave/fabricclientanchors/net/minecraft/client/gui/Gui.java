package net.minecraft.client.gui;

import java.util.Stack;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.ClientHooks;

/** Hand-written stand-in, not game code: the merged Gui hands the top screen and its layers to NeoForge's hook. */
public class Gui {
	public Screen screen;
	public final Stack<Screen> layers = new Stack<>();

	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (screen != null) ClientHooks.extractScreen(screen, layers, graphics, mouseX, mouseY, partialTick);
	}
}
