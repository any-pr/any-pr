package com.zurrtum.create.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fixture.createhud.TrainHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Shaped like Create Fly's HudMixin train overlay, written against vanilla's extractHotbarAndDecorations: it wraps the
 * nextContextualInfoState() call, draws the train overlay with the method's graphics and timing, and then hides the
 * contextual bar.
 */
@Mixin(Hud.class)
public class HudMixin {
	@Shadow @Final private Minecraft minecraft;

	@WrapOperation(method = "extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;nextContextualInfoState()Lnet/minecraft/client/gui/Hud$ContextualInfo;"))
	private Hud.ContextualInfo renderMainHud(Hud instance, Operation<Hud.ContextualInfo> original,
			@Local(argsOnly = true) GuiGraphicsExtractor graphics, @Local(argsOnly = true) DeltaTracker deltaTracker) {
		if (TrainHud.renderOverlay(minecraft, graphics, deltaTracker)) {
			return Hud.ContextualInfo.EMPTY;
		}
		return original.call(instance);
	}
}
