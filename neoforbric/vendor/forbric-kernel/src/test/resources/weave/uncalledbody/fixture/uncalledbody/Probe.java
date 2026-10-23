package fixture.uncalledbody;

import net.minecraft.world.item.ItemStack;

/** Builds a tooltip the way the merged game does, through the dispatcher; returns the trace. */
public class Probe {
	public String run() {
		ItemStack stack = new ItemStack();
		stack.addDetailsToTooltip(null, null, null, null, line -> { });
		return "stack[" + stack.trace + "]";
	}
}
