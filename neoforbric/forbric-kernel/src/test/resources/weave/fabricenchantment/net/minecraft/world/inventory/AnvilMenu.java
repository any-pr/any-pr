package net.minecraft.world.inventory;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Hand-written stand-in, not game code: the merged anvil. {@code createResult} only calls the internal method, where
 * the one native question is asked.
 */
public class AnvilMenu {
	public ItemStack input;
	public Holder<Enchantment> book;
	public String result = "none";

	public void createResult() {
		createResultInternal();
	}

	private void createResultInternal() {
		if (input.supportsEnchantment(book)) {
			input.enchant(book);
			result = input.toString();
		}
	}
}
