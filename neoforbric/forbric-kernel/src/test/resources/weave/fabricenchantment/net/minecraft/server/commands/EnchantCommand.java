package net.minecraft.server.commands;

import java.util.Collection;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Hand-written stand-in, not game code: the merged /enchant asks the stack's native question, once. */
public final class EnchantCommand {
	private EnchantCommand() {
	}

	public static int enchant(CommandSourceStack source, Collection<? extends Entity> targets, Holder<Enchantment> enchantment, int level) {
		int applied = 0;
		for (Entity target : targets) {
			ItemStack stack = target.getMainHandItem();
			if (stack.supportsEnchantment(enchantment)) {
				stack.enchant(enchantment);
				applied++;
			}
		}
		return applied;
	}
}
