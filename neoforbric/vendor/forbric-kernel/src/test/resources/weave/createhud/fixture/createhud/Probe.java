package fixture.createhud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;

/** One frame of the HUD while riding a train, then one with the HUD hidden (F1): what each frame drew. */
public class Probe {
	public String probe() {
		Minecraft onTrain = new Minecraft(true);
		return "shown: " + frame(new Hud(onTrain, false)) + " | hidden: " + frame(new Hud(onTrain, true));
	}

	private static String frame(Hud hud) {
		GuiGraphicsExtractor graphics = new GuiGraphicsExtractor();
		hud.extractRenderState(graphics, new DeltaTracker());
		return String.join(", ", graphics.drawn);
	}
}
