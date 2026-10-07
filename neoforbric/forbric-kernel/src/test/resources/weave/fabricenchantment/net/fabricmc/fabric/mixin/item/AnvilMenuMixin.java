package net.fabricmc.fabric.mixin.item;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Synthetic guest mixin in the shape of fabric-item-api's anvil hook: vanilla's {@code canEnchant} in
 * {@code createResult}, redirected to the per-item question by an instance handler the fingerprint pins.
 */
@Mixin(AnvilMenu.class)
abstract class AnvilMenuMixin {
	@Redirect(method = "createResult", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"))
	private boolean callAllowEnchantingEvent(Enchantment enchantment, ItemStack stack, Holder<Enchantment> holder) {
		return stack.canBeEnchantedWith(holder, EnchantingContext.ACCEPTABLE);
	}
}
