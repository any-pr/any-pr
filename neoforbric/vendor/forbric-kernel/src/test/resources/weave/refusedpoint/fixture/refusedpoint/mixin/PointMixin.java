package fixture.refusedpoint.mixin;

import java.util.Optional;

import net.minecraft.server.PointRelay;
import net.minecraft.server.PointServer;
import net.minecraft.server.PointTrail;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A name-only hook written for vanilla's two arguments at a call vanilla's body makes, on two targets: the relay still
 * has that method and the call; the server's name binds the carrier's overload, which makes no such call. Then a tick
 * hook on both. require = 0, as a mod that tolerates a missing point writes it.
 */
@Mixin({PointServer.class, PointRelay.class})
public abstract class PointMixin {
	@Inject(method = "handle", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PointRelay;note(Ljava/lang/String;)V"),
			require = 0)
	private void onNote(String id, Optional<String> payload, CallbackInfo ci) {
		PointTrail.add("[mod saw " + id + "] ");
	}

	@Inject(method = "tickServer", at = @At("HEAD"))
	private void onTick(CallbackInfo ci) {
		PointTrail.add("[mod tick] ");
	}
}
