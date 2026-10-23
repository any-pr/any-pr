package fixture.refusedbinding.mixin;

import java.util.Optional;

import net.minecraft.server.ClickServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A name-only hook written for vanilla's two arguments, declared first, and a tick hook that has nothing to do with
 * overloads: when Mixin rejects the first, the second goes with it.
 */
@Mixin(ClickServer.class)
public abstract class ClickServerMixin {
	@Inject(method = "handleCustomClickAction", at = @At("HEAD"))
	private void onClick(String id, Optional<String> payload, CallbackInfo ci) {
		((ClickServer) (Object) this).trail.append("[mod saw ").append(id).append("] ");
	}

	@Inject(method = "tickServer", at = @At("HEAD"))
	private void onTick(CallbackInfo ci) {
		((ClickServer) (Object) this).trail.append("[mod tick] ");
	}
}
