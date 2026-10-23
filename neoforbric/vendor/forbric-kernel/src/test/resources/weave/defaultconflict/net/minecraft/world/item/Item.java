package net.minecraft.world.item;

import net.minecraftforge.fixture.defaultconflict.IForgeItem;
import net.neoforged.fixture.defaultconflict.IItemExtension;

/**
 * The merged base's Item: both Forge-family extensions, whose remainder defaults are overloads of each other and so
 * no conflict until a third interface defaults the ItemStack one again.
 */
public class Item implements IForgeItem, IItemExtension {
	private final ItemStackTemplate craftingRemainder;

	public Item(ItemStackTemplate craftingRemainder) {
		this.craftingRemainder = craftingRemainder;
	}

	public ItemStackTemplate getCraftingRemainder() {
		return craftingRemainder;
	}

	/** Branches, so this class carries a stack map that the post-Mixin rewrite must hand back intact. */
	public boolean hasCraftingRemainder() {
		return craftingRemainder != null && !craftingRemainder.id().isEmpty();
	}
}
