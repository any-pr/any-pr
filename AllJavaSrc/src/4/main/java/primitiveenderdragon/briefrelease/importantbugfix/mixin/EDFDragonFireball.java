package primitiveenderdragon.briefrelease.importantbugfix.mixin;

import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DragonFireball.class)
public class EDFDragonFireball {

    @Redirect(
            method = "onHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/AreaEffectCloud;setRadius(F)V",
                    ordinal = 0
            )
    )
    private void redirectSetRadius(AreaEffectCloud cloud, float radius) {
        cloud.setRadius(6.0F);
    }

    @Redirect(
            method = "onHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/AreaEffectCloud;setDuration(I)V",
                    ordinal = 0
            )
    )
    private void redirectSetDuration(AreaEffectCloud cloud, int duration) {
        cloud.setDuration(2400);
    }

    @Redirect(
            method = "onHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/AreaEffectCloud;setRadiusPerTick(F)V",
                    ordinal = 0
            )
    )
    private void redirectSetRadiusPerTick(AreaEffectCloud cloud, float radiusPerTick) {
        cloud.setRadiusPerTick(-0.0025F);
    }

    @Redirect(
            method = "onHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/AreaEffectCloud;setPotionDurationScale(F)V",
                    ordinal = 0
            )
    )
    private void redirectSetPotionDurationScale(AreaEffectCloud cloud, float durationScale) {
        cloud.setPotionDurationScale(1.0F);
    }
}
