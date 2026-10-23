package fixture.localscapture.mixin;

import fixture.localscapture.BlockBreaker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(BlockBreaker.class)
public abstract class BlockBreakerMixin {
	/** Captures (block, state) as the unpatched body declares them; the merged body has an int between the two. */
	@Inject(method = "destroyBlock", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
	private void onDestroy(CallbackInfoReturnable<String> result, String block, String state) {
		System.out.println("[LocalsCapture] mismatched capture ran with " + block + "," + state);
	}

	/** Also FAIL_HARD, but written against the body as it is: softening must not stop it receiving the real values. */
	@Inject(method = "destroyBlock", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
	private void fits(CallbackInfoReturnable<String> result, String block, int xp, String state) {
		System.out.println("[LocalsCapture] fitting capture saw " + block + "," + xp + "," + state);
	}

	/** Captures nothing, so it fits the merged body: it runs whenever this mixin class applies at all. */
	@Inject(method = "destroyBlock", at = @At("RETURN"), cancellable = true)
	private void tagged(CallbackInfoReturnable<String> result) {
		result.setReturnValue(result.getReturnValue() + "|mixin");
	}
}
