package fixture.unfit.mixin;

import fixture.unfit.Stamped;
import net.minecraft.fixture.unfit.Widget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Written against vanilla's Widget: its only anchor is oldLabel(), which the merged class does not have, so nothing it
 * hooks can run. Applied anyway, it would still hand Widget its interface, and the mod's casts would succeed on an
 * object whose hook never fires.
 */
@Mixin(Widget.class)
public abstract class StaleWidgetMixin implements Stamped {
	@Unique private String stale$stamp = "never stamped";

	@Inject(method = "oldLabel", at = @At("HEAD"))
	private void onOldLabel(CallbackInfoReturnable<String> result) {
		stale$stamp = "stamped by oldLabel";
	}

	@Override
	public String stamp() {
		return stale$stamp;
	}
}
