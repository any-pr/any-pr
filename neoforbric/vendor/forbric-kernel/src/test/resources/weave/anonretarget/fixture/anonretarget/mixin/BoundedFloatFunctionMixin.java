package fixture.anonretarget.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Written the way a mod compiled against VANILLA writes it: there, {@code BoundedFloatFunction$2} is the body that
 * calls {@code tag}, so that is the number it targets and the owner its INVOKE point pins.
 *
 * <p>The INVOKE target is spelled {@code owner.name(desc)}. The equally valid {@code Lowner;name(desc)} spelling is
 * not unpinned by {@code MixinMergedTwin.unpinInjectionPointOwners} today, so the argument half would be lost.
 */
@Mixin(targets = "net.minecraft.util.BoundedFloatFunction$2")
public abstract class BoundedFloatFunctionMixin {
	@Inject(method = "describe", at = @At("RETURN"), cancellable = true)
	private void anonretarget$markReturn(String input, CallbackInfoReturnable<String> result) {
		result.setReturnValue(result.getReturnValue() + "+guest-return");
	}

	@ModifyArg(method = "describe", at = @At(value = "INVOKE",
			target = "net/minecraft/util/BoundedFloatFunction$2.tag(Ljava/lang/String;)Ljava/lang/String;"))
	private String anonretarget$markArgument(String input) {
		return input + "+guest-arg";
	}
}
