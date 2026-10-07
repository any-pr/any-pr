package fixture.overloadorder.mixin;

import java.util.Optional;

import fixture.overloadorder.ClickServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Written the way carpet-org-addition writes its dialog hook against vanilla, where the name is unique: name only, and a
 * handler shaped for vanilla's {@code (Identifier, Optional)}. The second injection is there to show what a failure
 * costs: Mixin fails the whole class, so it dies with the first.
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
