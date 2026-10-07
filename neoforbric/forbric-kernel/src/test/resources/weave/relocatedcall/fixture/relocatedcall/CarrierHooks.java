package fixture.relocatedcall;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Stands in for the carrier's hook class that took the Item.useOn call out of ItemStack.useOn. */
public final class CarrierHooks {
	private CarrierHooks() {
	}

	public static InteractionResult placeItem(ItemStack stack, UseOnContext context) {
		return stack.forbric$useOnItem(stack.getItem(), context);
	}
}
