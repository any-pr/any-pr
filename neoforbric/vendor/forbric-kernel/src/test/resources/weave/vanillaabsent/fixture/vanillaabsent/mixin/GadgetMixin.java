package fixture.vanillaabsent.mixin;

import fixture.vanillaabsent.Tagged;
import net.minecraft.fixture.vanillaabsent.Gadget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Not Enough Crashes' MixinTileEntity in shape: a field of its own with an initializer, and one injector whose target
 * the game does not have, in a required config with no defaultRequire. Natively Mixin skips that injector without a
 * word and applies the rest; here it adds the interface too, so the probe can see that the rest arrived.
 */
@Mixin(Gadget.class)
public abstract class GadgetMixin implements Tagged {
	@Unique private String vanillaabsent$tag = "kept";

	@Inject(method = "populateReport", at = @At("TAIL"))
	private void onPopulateReport(CallbackInfo ci) {
		vanillaabsent$tag = "reported";
	}

	@Override
	public String tag() {
		return vanillaabsent$tag;
	}
}
