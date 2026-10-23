package net.neoforged.fixture.defaultconflict;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStackTemplate;

public interface IItemExtension {
	default ItemStackTemplate getCraftingRemainder(ItemInstance stack) {
		return ((Item) this).getCraftingRemainder();
	}
}
