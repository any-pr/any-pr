package fixture.createhud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The mod's train overlay: drawn while the player rides a train, in place of the contextual bar. */
public final class TrainHud {
	private TrainHud() {
	}

	public static boolean renderOverlay(Minecraft minecraft, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!minecraft.onTrain) return false;
		graphics.drawn.add("train overlay");
		return true;
	}
}
