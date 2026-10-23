package fixture.guiitemcapture;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.world.item.ItemStack;

/** What the guest's handlers were handed: the item and the render state it was captured with, and the tooltips. */
public final class Outlines {
	public static final List<String> CAPTURED = new ArrayList<>();
	public static final List<String> TOOLTIPS = new ArrayList<>();

	private Outlines() {
	}

	public static void capture(ItemStack stack, TrackingItemStackRenderState state) {
		CAPTURED.add(stack + "->" + state);
	}

	public static void tooltipScheduled(int x, int y) {
		TOOLTIPS.add(x + "," + y);
	}
}
