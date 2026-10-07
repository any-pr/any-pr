package net.fabricmc.fabric.mixin.screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Synthetic guest mixin in the shape of fabric-screen-api's: the per-screen events around vanilla's screen draw. */
@Mixin(Gui.class)
abstract class GuiMixin {
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
	private void onExtractGui(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, Operation<Void> original) {
		ScreenEvents.beforeExtract(screen).invoker().beforeExtract(screen, graphics, mouseX, mouseY, partialTick);
		original.call(screen, graphics, mouseX, mouseY, partialTick);
		ScreenEvents.afterExtract(screen).invoker().afterExtract(screen, graphics, mouseX, mouseY, partialTick);
	}
}
