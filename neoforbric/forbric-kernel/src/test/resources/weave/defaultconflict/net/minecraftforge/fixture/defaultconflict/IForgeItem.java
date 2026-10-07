package net.minecraftforge.fixture.defaultconflict;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

public interface IForgeItem {
	default ItemStackTemplate getCraftingRemainder(ItemStack stack) {
		return ((Item) this).getCraftingRemainder();
	}
}
