package net.minecraft.world.item.enchantment;

import net.minecraft.world.item.ItemStack;

/** Hand-written stand-in, not game code. {@code canEnchant} is vanilla's question; the merged bodies no longer ask it. */
public record Enchantment(String name) {
	public boolean canEnchant(ItemStack stack) {
		return stack.supportsEnchantment(new net.minecraft.core.Holder<>(this));
	}
}
