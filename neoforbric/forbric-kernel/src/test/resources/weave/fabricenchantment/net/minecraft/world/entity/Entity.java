package net.minecraft.world.entity;

import net.minecraft.world.item.ItemStack;

/** Hand-written stand-in, not game code: an entity holding one item. */
public class Entity {
	private final ItemStack mainHand;

	public Entity(ItemStack mainHand) {
		this.mainHand = mainHand;
	}

	public ItemStack getMainHandItem() {
		return mainHand;
	}
}
