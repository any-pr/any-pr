package net.fabricmc.fabric.mixin.registry.sync;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fixture.fabricfreezehook.Trace;
import net.minecraft.server.Bootstrap;

/**
 * Synthetic guest mixin standing in for fabric-registry-sync, whose registered config is all the adapter asks about:
 * with it, a Fabric game freezes after the mains. It only marks the end of the bootstrap, so the trace shows it wove.
 */
@Mixin(Bootstrap.class)
public class BootstrapTrackersMixin {
	@Inject(method = "bootStrap", at = @At("TAIL"))
	private static void afterBootstrap(CallbackInfo info) {
		Trace.add("registrySync");
	}
}
