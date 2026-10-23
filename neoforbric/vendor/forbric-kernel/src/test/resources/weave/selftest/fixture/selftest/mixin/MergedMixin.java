package fixture.selftest.mixin;

import fixture.selftest.Merged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Merged.class)
public abstract class MergedMixin {
	/** Woven into the class the chain handed Mixin: label() answers "merged|woven" whatever the chain did. */
	@Inject(method = "label", at = @At("RETURN"), cancellable = true)
	private void woven(CallbackInfoReturnable<String> result) {
		result.setReturnValue(result.getReturnValue() + "|woven");
	}
}
