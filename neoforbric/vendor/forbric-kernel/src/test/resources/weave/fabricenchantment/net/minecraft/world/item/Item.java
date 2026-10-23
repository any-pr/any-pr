package net.minecraft.world.item;

import java.util.Set;

import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Hand-written stand-in, not game code. {@code canBeEnchantedWith} is Fabric's per-item question, whose default (as
 * the kernel's item contract leaves it) is the native answer; a Fabric item overrides it.
 */
public class Item {
	private final String name;
	private final Set<String> nativeEnchantments;

	public Item(String name, Set<String> nativeEnchantments) {
		this.name = name;
		this.nativeEnchantments = nativeEnchantments;
	}

	public String name() {
		return name;
	}

	boolean nativelySupports(Enchantment enchantment) {
		return nativeEnchantments.contains(enchantment.name());
	}

	public boolean canBeEnchantedWith(ItemStack stack, Holder<Enchantment> enchantment, EnchantingContext context) {
		return stack.supportsEnchantment(enchantment);
	}
}
