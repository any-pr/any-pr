package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Hand-written stand-in, not game code: {@code supportsEnchantment} is NeoForge's native question, the one the merged
 * bodies ask; {@code canBeEnchantedWith} is Fabric's, injected into the stack.
 */
public class ItemStack {
	private final Item item;
	private final List<String> enchantments = new ArrayList<>();

	public ItemStack(Item item) {
		this.item = item;
	}

	public boolean supportsEnchantment(Holder<Enchantment> enchantment) {
		return item.nativelySupports(enchantment.value());
	}

	public boolean canBeEnchantedWith(Holder<Enchantment> enchantment, EnchantingContext context) {
		return item.canBeEnchantedWith(this, enchantment, context);
	}

	public void enchant(Holder<Enchantment> enchantment) {
		enchantments.add(enchantment.value().name());
	}

	@Override
	public String toString() {
		return item.name() + enchantments;
	}
}
