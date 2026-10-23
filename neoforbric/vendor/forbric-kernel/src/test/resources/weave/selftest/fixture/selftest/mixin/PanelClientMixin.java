package fixture.selftest.mixin;

import fixture.selftest.Panel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Panel.class)
public abstract class PanelClientMixin {
	@Inject(method = "title", at = @At("HEAD"), cancellable = true)
	private void client(CallbackInfoReturnable<String> result) {
		result.setReturnValue("client");
	}
}
