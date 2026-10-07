package fixture.subtypeowner.mixin;

import java.util.List;

import net.minecraft.subtypeowner.EntryLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A guest mixin compiled against vanilla, where each of these bodies decodes through Decoder.parse (lithostitched's
 * Fabric predicate check has this shape). No handler takes a @Local: the fixture is compiled without a local variable
 * table, and the retarget only moves a handler whose named locals it can see at the call.
 */
@Mixin(EntryLoader.class)
public abstract class EntryLoaderMixin {
	@Shadow @Final private List<String> trace;

	@Inject(method = "loadMerged", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Decoder;parse(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"))
	private void gateMerged(CallbackInfoReturnable<String> cir) {
		trace.add("gate");
	}

	@Inject(method = "loadMixed", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Decoder;parse(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"))
	private void gateMixed(CallbackInfoReturnable<String> cir) {
		trace.add("gate");
	}

	@Inject(method = "loadTwice", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Decoder;parse(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"))
	private void gateTwice(CallbackInfoReturnable<String> cir) {
		trace.add("gate");
	}
}
