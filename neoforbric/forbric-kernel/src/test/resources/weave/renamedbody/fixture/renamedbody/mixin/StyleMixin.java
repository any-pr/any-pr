package fixture.renamedbody.mixin;

import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mixin naming withColor(I) for a call withColor(I) does not make, as text_styles' does. withShadowColor(I) has the
 * same shape and makes it; moving there would run a colour hook on every shadow colour, which no game ever did.
 */
@Mixin(Style.class)
public abstract class StyleMixin {
	@Shadow @Final public StringBuilder trace;

	@Inject(method = "withColor(I)Lnet/minecraft/network/chat/Style;", at = @At("HEAD"))
	private void renamedbody$colorHead(int color, CallbackInfoReturnable<Style> cir) {
		trace.append("colorHead;");
	}

	@Inject(method = "withColor(I)Lnet/minecraft/network/chat/Style;", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/chat/Style;checkEmptyAfterChange(Lnet/minecraft/network/chat/Style;Ljava/lang/Object;Ljava/lang/Object;)Lnet/minecraft/network/chat/Style;"))
	private void renamedbody$colorChange(int color, CallbackInfoReturnable<Style> cir) {
		trace.append("colorChange;");
	}
}
