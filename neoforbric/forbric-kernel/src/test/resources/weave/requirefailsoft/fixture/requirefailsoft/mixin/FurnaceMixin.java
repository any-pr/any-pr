package fixture.requirefailsoft.mixin;

import net.minecraft.fixture.requirefailsoft.Furnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Furnace.class)
public abstract class FurnaceMixin {
	/**
	 * The mod's own hard requirement, which the config-level relaxation cannot lower: Mixin takes require over the
	 * config's defaultRequire, and a missed count is an InjectionError that abandons the whole target class.
	 */
	@Inject(method = "burn", at = @At(value = "INVOKE", target = "Lnet/minecraft/fixture/requirefailsoft/Furnace;consumeFuel()V"), require = 1)
	private void onFuel(CallbackInfoReturnable<String> result) {
		throw new AssertionError("absent site executed");
	}

	/** Fits the merged body, so the mixin is kept: it runs whenever the target class is defined at all. */
	@Inject(method = "burn", at = @At("RETURN"), cancellable = true)
	private void lit(CallbackInfoReturnable<String> result) {
		result.setReturnValue(result.getReturnValue() + "|mixin");
	}
}
