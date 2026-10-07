package forbric.mixincanary.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

/**
 * Names a method the game does not have, so the kernel's fit check leaves it out — and must say whose it was.
 *
 * <p>{@code require = 1} is what keeps it a loss. Vanilla lacks the method too, and an injector nothing requires to
 * inject is one native Mixin drops without a word while applying the rest; the kernel now drops that one the same way
 * (NativeAbsentTargets) and there would be nothing to attribute. An injector that must inject is a failure natively
 * as well.
 */
@Mixin(MinecraftServer.class)
public abstract class UnfitMixin {
	@Inject(method = "forbricMethodThatDoesNotExist", at = @At("HEAD"), require = 1)
	private void forbric$unfit(CallbackInfo ci) {
	}
}
