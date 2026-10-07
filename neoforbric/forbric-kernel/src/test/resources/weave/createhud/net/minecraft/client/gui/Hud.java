package net.minecraft.client.gui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

/**
 * A stand-in for the merged base's Hud: NeoForge's extractRenderState picks the contextual bar through
 * updateContextualBarRenderer and then draws its layers. Vanilla's extractHotbarAndDecorations is still declared and
 * still asks nextContextualInfoState, but the merged game no longer calls it.
 */
public class Hud {
	private final Minecraft minecraft;
	private final boolean isHidden;
	private ContextualInfo contextualInfoBar = ContextualInfo.EMPTY;

	public Hud(Minecraft minecraft, boolean isHidden) {
		this.minecraft = minecraft;
		this.isHidden = isHidden;
	}

	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		this.updateContextualBarRenderer();
		graphics.drawn.add("bar " + contextualInfoBar);
	}

	private void updateContextualBarRenderer() {
		this.contextualInfoBar = this.nextContextualInfoState();
	}

	public void extractHotbarAndDecorations(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		graphics.drawn.add("vanilla bar " + this.nextContextualInfoState());
	}

	public ContextualInfo nextContextualInfoState() {
		return ContextualInfo.EXPERIENCE;
	}

	public boolean isHidden() {
		return isHidden;
	}

	public enum ContextualInfo {
		EMPTY, EXPERIENCE, LOCATOR
	}
}
