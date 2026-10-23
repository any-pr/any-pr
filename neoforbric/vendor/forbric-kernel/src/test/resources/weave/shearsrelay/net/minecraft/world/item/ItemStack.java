package net.minecraft.world.item;

import net.minecraft.core.TypedInstance;
import net.neoforged.neoforge.common.ItemAbility;

/** Fixture stand-in: vanilla's typed is(item) and NeoForge's ability question, asked of the item. */
public final class ItemStack implements TypedInstance<Item> {
	private final Item item;

	public ItemStack(Item item) {
		this.item = item;
	}

	@Override
	public Item typeInstance() {
		return item;
	}

	public Item getItem() {
		return item;
	}

	public boolean canPerformAction(ItemAbility ability) {
		return item.canPerformAction(this, ability);
	}
}
