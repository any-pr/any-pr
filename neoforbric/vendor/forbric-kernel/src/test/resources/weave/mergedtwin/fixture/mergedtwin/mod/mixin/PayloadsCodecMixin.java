package fixture.mergedtwin.mod.mixin;

import fixture.mergedtwin.mod.ChannelHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Written against the vanilla game, which has one anonymous codec and calls it Payloads$1: a String target,
 * an owner-pinned INVOKE point in the dotted form Bad Packets uses, and a default (remappable) @Shadow.
 */
@Mixin(targets = "fixture.mergedtwin.Payloads$1")
public abstract class PayloadsCodecMixin implements ChannelHolder {
	@Shadow
	public abstract String findCodec(String id);

	@Inject(method = "encode", at = @At(value = "INVOKE",
			target = "fixture/mergedtwin/Payloads$1.findCodec(Ljava/lang/String;)Ljava/lang/String;"), cancellable = true)
	private void mergedtwin$encode(String id, CallbackInfoReturnable<String> result) {
		result.setReturnValue("woven:" + id);
	}

	@Override
	public String mergedtwin$channel() {
		return "channel-" + findCodec("probe");
	}
}
