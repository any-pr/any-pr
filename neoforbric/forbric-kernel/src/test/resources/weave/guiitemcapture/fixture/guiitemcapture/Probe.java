package fixture.guiitemcapture;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Draws a diamond, an empty slot and a tooltip; reports what the guest captured and what the GUI submitted. */
public class Probe {
	public String probe() {
		GuiGraphicsExtractor gui = new GuiGraphicsExtractor();
		gui.item(null, null, new ItemStack("diamond"), 1, 2, 7);
		gui.item(null, null, ItemStack.EMPTY, 3, 4, 8);
		gui.setTooltipForNextFrame(new Component("hint"), 5, 6);
		return "captured=" + Outlines.CAPTURED + " tooltips=" + Outlines.TOOLTIPS + " submitted=" + gui.submitted();
	}
}
