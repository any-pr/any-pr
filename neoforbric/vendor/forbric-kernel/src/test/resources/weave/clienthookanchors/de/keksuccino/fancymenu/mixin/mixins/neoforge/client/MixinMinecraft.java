package de.keksuccino.fancymenu.mixin.mixins.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A client-init hook in FancyMenu's NeoForge shape: a plain constructor @Inject right after NeoForge's
 * ClientHooks.initClientHooks, with the constructor's arguments and a CallbackInfo. The body is the fixture's own.
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {
	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;initClientHooks(Lnet/minecraft/client/Minecraft;Lnet/minecraft/server/packs/resources/ReloadableResourceManager;)V",
			shift = At.Shift.AFTER))
	private void after_initClientHooks_NeoForge_FancyMenu(GameConfig gameConfig, CallbackInfo info) {
		((Minecraft) (Object) this).trace.add("fancymenu init");
	}
}
