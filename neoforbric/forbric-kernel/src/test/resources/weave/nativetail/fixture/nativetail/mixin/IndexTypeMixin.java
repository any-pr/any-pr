package fixture.nativetail.mixin;

import com.mojang.blaze3d.IndexType;
import fixture.nativetail.Seen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IndexType.class)
public abstract class IndexTypeMixin {
	/**
	 * Written the way a NeoForge or MinecraftForge mod writes it against its own carrier, where least's body is folded
	 * and TAIL is the return every call leaves through: it counts every choice, INT included.
	 */
	@Inject(method = "least", at = @At("TAIL"))
	private static void nativetail$countEveryChoice(int count, CallbackInfoReturnable<IndexType> chosen) {
		Seen.choice(chosen.getReturnValue());
	}
}
