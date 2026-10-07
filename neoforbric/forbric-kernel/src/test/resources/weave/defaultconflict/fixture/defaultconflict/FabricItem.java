package fixture.defaultconflict;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/** The mod's item extension: against vanilla, the only stack-aware remainder there is. */
public interface FabricItem {
	default ItemStackTemplate getCraftingRemainder(ItemStack stack) {
		return ((Item) this).getCraftingRemainder();
	}
}
