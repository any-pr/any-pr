package net.minecraft.world.level.block;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * Fixture stand-in for the merged pumpkin: it asks the stack for NeoForge's carving ability where vanilla asked
 * is(Items.SHEARS), and makes no other is() call.
 */
public class PumpkinBlock {
	public String useItemOn(ItemStack stack) {
		if (stack.canPerformAction(ItemAbilities.SHEARS_CARVE)) {
			return "carved";
		}
		return "pass";
	}
}
