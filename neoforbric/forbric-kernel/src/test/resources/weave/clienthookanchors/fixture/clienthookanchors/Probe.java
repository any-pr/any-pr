package fixture.clienthookanchors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;

/** Builds the client and reports what its constructor ran, in order. */
public class Probe {
	public String probe() {
		return String.valueOf(new Minecraft(new GameConfig("cfg")).trace);
	}
}
