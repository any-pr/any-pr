package fixture.fabriccreativepager;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

/** PageDown then PageUp on one screen, reading the page the screen draws after each. */
public class Probe {
	public String run() {
		CreativeModeInventoryScreen screen = new CreativeModeInventoryScreen();
		boolean down = screen.keyPressed(267);
		int afterDown = screen.drawnPage();
		boolean up = screen.keyPressed(266);
		return "down=" + down + " drawn=" + afterDown + " up=" + up + " drawn=" + screen.drawnPage();
	}
}
