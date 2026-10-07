package fixture.vanillaabsent;

import net.minecraft.fixture.vanillaabsent.Gadget;

/** Reports what the defined Gadget is: its label, and whether the mixin's interface and field came with it. */
public class Probe {
	public String probe() {
		Object gadget = new Gadget();
		StringBuilder report = new StringBuilder();
		((Gadget) gadget).fillReport(report);
		String tag = gadget instanceof Tagged tagged ? "tagged " + tagged.tag() : "untagged";
		return report + " " + tag;
	}
}
