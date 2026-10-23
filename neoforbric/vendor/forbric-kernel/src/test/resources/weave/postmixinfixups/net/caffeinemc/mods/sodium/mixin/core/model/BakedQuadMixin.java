package net.caffeinemc.mods.sodium.mixin.core.model;

import fixture.postmixinfixups.QuadView;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A quad mixin in Sodium's shape: it computes its own per-quad field in a RETURN injection into the vanilla-shaped
 * constructor, the only one it knew. The body is the fixture's own.
 */
@Mixin(BakedQuad.class)
public abstract class BakedQuadMixin implements QuadView {
	@Unique private String normalFace;

	@Inject(method = "<init>([IILjava/lang/String;)V", at = @At("RETURN"))
	private void computeNormalFace(int[] vertices, int tintIndex, String direction, CallbackInfo ci) {
		normalFace = "face(" + direction + ")";
	}

	@Override
	public String getNormalFace() {
		return normalFace;
	}
}
