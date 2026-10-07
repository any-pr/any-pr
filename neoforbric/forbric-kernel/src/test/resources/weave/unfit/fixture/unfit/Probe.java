package fixture.unfit;

import net.minecraft.fixture.unfit.Widget;

/** Reports what the defined Widget does: its label, and whether the stale mixin's interface came with it. */
public class Probe {
	public String probe() {
		Object widget = new Widget();
		String stamp = widget instanceof Stamped stamped ? "stamped " + stamped.stamp() : "unstamped";
		return ((Widget) widget).label() + " " + stamp;
	}
}
