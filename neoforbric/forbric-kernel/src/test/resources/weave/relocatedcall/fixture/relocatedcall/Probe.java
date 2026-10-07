package fixture.relocatedcall;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Uses an item the way the game does; the answer says whether the guest's wrap of Item.useOn ran. */
public class Probe {
	public String use() {
		return new ItemStack(new Item()).useOn(new UseOnContext()).name();
	}
}
