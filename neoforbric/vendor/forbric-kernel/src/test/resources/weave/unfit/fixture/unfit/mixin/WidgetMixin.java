package fixture.unfit.mixin;

import net.minecraft.fixture.unfit.Widget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Widget.class)
public abstract class WidgetMixin {
	/** Fits the merged class, so it is kept beside its unfit sibling and runs. */
	@Inject(method = "label", at = @At("RETURN"), cancellable = true)
	private void tagged(CallbackInfoReturnable<String> result) {
		result.setReturnValue(result.getReturnValue() + "|mixin");
	}
}
