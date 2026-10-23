package fixture.anonretarget;

import net.minecraft.util.BoundedFloatFunction;

/** Calls both anonymous classes, so the probe line shows which one the guest mixin landed on. */
public class Probe {
	public String run() {
		return BoundedFloatFunction.MOVED_BODY.describe("x") + "|" + BoundedFloatFunction.NUMBER_HEIR.describe("x");
	}
}
