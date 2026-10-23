package forbric.mixincanary.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;

/**
 * Fits — read(ChunkPos) exists, and one handler is written for it — and fails at APPLY: the second handler takes a
 * CallbackInfo where a CompoundTag-returning target needs a CallbackInfoReturnable, and Mixin refuses its descriptor at
 * the first point it finds. RegionFileStorage is named only by IOWorker and the world upgrader, so it is first loaded
 * when the world's region storage opens — after load-complete — and this failure can only reach the load report if the
 * report is written again once the world is up.
 *
 * <p>Two things keep it an apply failure rather than a mixin the fit check leaves out beforehand. The first handler
 * binds as written, so the verdict is PARTIAL and the mixin is kept; alone, the refused one would make it UNFIT. And the
 * refused one is at a JUMP: the fit check takes out a refused injector it can prove Mixin will meet (HEAD, TAIL, RETURN,
 * a counted INVOKE or FIELD), and a JUMP is not one it counts, so this one is left for Mixin to fail on.
 */
@Mixin(RegionFileStorage.class)
public abstract class ApplyFailingMixin {
	@Inject(method = "read", at = @At("HEAD"))
	private void forbric$fits(ChunkPos pos, CallbackInfoReturnable<CompoundTag> cir) {
	}

	@Inject(method = "read", at = @At(value = "JUMP", ordinal = 0))
	private void forbric$wrongCallback(CallbackInfo ci) {
	}
}
