package net.fabricmc.fabric.mixin.item;

import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Synthetic guest mixin in the shape of fabric-item-api's: vanilla's {@code canEnchant} in {@code enchant} is
 * redirected to the per-item question. The handler is the one delegating call the adapter's fingerprint pins.
 */
@Mixin(EnchantCommand.class)
abstract class EnchantCommandMixin {
	@Redirect(method = "enchant", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"))
	private static boolean callAllowEnchantingEvent(Enchantment enchantment, ItemStack stack, CommandSourceStack source,
			Collection<? extends Entity> targets, Holder<Enchantment> holder) {
		return stack.canBeEnchantedWith(holder, EnchantingContext.ACCEPTABLE);
	}
}
