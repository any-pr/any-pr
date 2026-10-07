package net.minecraft.world.item;

import java.util.Set;

import net.neoforged.neoforge.common.ItemAbility;

/** Fixture stand-in: an item with the abilities it declares and the tags it carries. */
public class Item {
	private final String id;
	private final Set<ItemAbility> abilities;
	private final Set<String> tags;

	public Item(String id, Set<ItemAbility> abilities, Set<String> tags) {
		this.id = id;
		this.abilities = abilities;
		this.tags = tags;
	}

	public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
		return abilities.contains(ability);
	}

	public boolean tagged(String tag) {
		return tags.contains(tag);
	}

	@Override
	public String toString() {
		return id;
	}
}
