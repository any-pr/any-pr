package fixture.selftest.mixin;

import fixture.selftest.Greeter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Greeter.class)
public abstract class GreeterMixin {
	/** Present anchor: if it is woven and runs, greet() answers "woven". */
	@Inject(method = "greet", at = @At("HEAD"), cancellable = true)
	private void woven(CallbackInfoReturnable<String> result) {
		result.setReturnValue("woven");
	}

	/** Absent anchor under defaultRequire 1: never runs, and must be reported as a confirmed, required loss. */
	@Inject(method = "greet", at = @At(value = "INVOKE", target = "Ljava/lang/String;length()I"))
	private void missing(CallbackInfoReturnable<String> result) {
		throw new AssertionError("absent site executed");
	}
}
