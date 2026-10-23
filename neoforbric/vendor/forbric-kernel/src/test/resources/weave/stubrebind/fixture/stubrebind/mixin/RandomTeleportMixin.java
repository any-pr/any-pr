package fixture.stubrebind.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Written the way a Fabric mod writes it against vanilla, where randomTeleport(DDDZ)Z is the one method of that name
 * and carries the body. On the fixture's widened class, Mixin binds both name-only selectors to the stub.
 */
@Mixin(LivingEntity.class)
public abstract class RandomTeleportMixin {
	@Shadow @Final public StringBuilder trace;

	/** Captures vanilla's four arguments: once moved, it must still receive exactly those. */
	@Inject(method = "randomTeleport", at = @At("HEAD"))
	private void stubrebind$head(double x, double y, double z, boolean broadcast, CallbackInfoReturnable<Boolean> cir) {
		trace.append("head(").append(x).append(',').append(y).append(',').append(z).append(',').append(broadcast).append(");");
	}

	/** An anchor in vanilla's body, which the stub does not have. */
	@Inject(method = "randomTeleport", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;land(DDD)Z"))
	private void stubrebind$beforeLand(CallbackInfoReturnable<Boolean> cir) {
		trace.append("anchor;");
	}
}
