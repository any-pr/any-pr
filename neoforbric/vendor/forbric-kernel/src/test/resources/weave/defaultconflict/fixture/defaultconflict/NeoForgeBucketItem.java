package fixture.defaultconflict;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStackTemplate;

/** A NeoForge mod's item: it overrides only NeoForge's overload, which a stack-typed call must still reach. */
public class NeoForgeBucketItem extends Item {
	public NeoForgeBucketItem() {
		super(new ItemStackTemplate("bucket"));
	}

	@Override
	public ItemStackTemplate getCraftingRemainder(ItemInstance stack) {
		return new ItemStackTemplate("neoforge-override:" + stack);
	}
}
