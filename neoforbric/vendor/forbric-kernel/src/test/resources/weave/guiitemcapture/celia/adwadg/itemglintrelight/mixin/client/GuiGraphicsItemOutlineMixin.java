package celia.adwadg.itemglintrelight.mixin.client;

import fixture.guiitemcapture.Outlines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * A GUI item capture in Item Glint Relight's shape: its class and handler names, the TAIL injector that captures the
 * render-state local with CAPTURE_FAILSOFT, and a HEAD tooltip hook beside it. The bodies are the fixture's own.
 */
@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsItemOutlineMixin {
	@Inject(method = "setTooltipForNextFrame(Lnet/minecraft/network/chat/Component;II)V", at = @At("HEAD"))
	private void itemglintrelight$tooltipScheduled(Component component, int x, int y, CallbackInfo ci) {
		Outlines.tooltipScheduled(x, y);
	}

	@Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
			at = @At("TAIL"), locals = LocalCapture.CAPTURE_FAILSOFT)
	private void itemglintrelight$captureGuiItem(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed,
			CallbackInfo ci, TrackingItemStackRenderState trackingItemStackRenderState) {
		Outlines.capture(stack, trackingItemStackRenderState);
	}
}
