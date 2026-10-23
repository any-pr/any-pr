package fixture.shearsrelay;

import java.util.Set;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.PumpkinBlock;

/** Uses vanilla shears, a modded shears item that only carries the tag, and a stick on a pumpkin. */
public class Probe {
	public String probe() {
		Item tagged = new Item("examplemod:flint_shears", Set.of(), Set.of("c:tools/shear"));
		PumpkinBlock pumpkin = new PumpkinBlock();
		return "shears=" + pumpkin.useItemOn(new ItemStack(Items.SHEARS))
				+ " tagged=" + pumpkin.useItemOn(new ItemStack(tagged))
				+ " stick=" + pumpkin.useItemOn(new ItemStack(Items.STICK));
	}
}
