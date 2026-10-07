package fixture.fabricregistryinit;

import net.minecraft.server.Bootstrap;
import net.minecraft.server.Main;

/** Boots the fake game once, the way a dedicated server does: bootstrap, then the main entry point. */
public class Probe {
	public String run() {
		Bootstrap.bootStrap();
		Main.main(new String[0]);
		return String.join(",", Trace.LINES);
	}
}
