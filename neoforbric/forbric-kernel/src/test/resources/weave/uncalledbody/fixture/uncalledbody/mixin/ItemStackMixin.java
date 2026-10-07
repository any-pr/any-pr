package fixture.uncalledbody.mixin;

import java.util.function.Consumer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * malilib's MixinItemStack, cut down: a HEAD hook, which binds in the dispatcher, and a hook after a component call of
 * vanilla's tooltip body, whose only copy here is the renamed body nothing calls. The mixin is required, as malilib's is.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Shadow @Final public StringBuilder trace;

	@Inject(method = "addDetailsToTooltip", at = @At("HEAD"))
	private void uncalledbody$first(Item.TooltipContext context, TooltipDisplay display, Player player, TooltipFlag flag,
			Consumer<String> out, CallbackInfo ci) {
		trace.append("first;");
	}

	@Inject(method = "addDetailsToTooltip", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/world/item/ItemStack;addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V"))
	private void uncalledbody$last(Item.TooltipContext context, TooltipDisplay display, Player player, TooltipFlag flag,
			Consumer<String> out, CallbackInfo ci) {
		trace.append("last;");
	}
}
