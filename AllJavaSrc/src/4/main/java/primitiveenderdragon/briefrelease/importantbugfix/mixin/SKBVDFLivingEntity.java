package primitiveenderdragon.briefrelease.importantbugfix.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class SKBVDFLivingEntity {

    @Inject(method = {"blockedByItem"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void fixshieldknockback(LivingEntity defender, CallbackInfo ci) {
        Object selfObject = this;
        LivingEntity attacker = (LivingEntity) selfObject;
        attacker.knockback((double)0.3F, defender.getX() - attacker.getX(), defender.getZ() - attacker.getZ());
        ci.cancel();
    }

    @Inject(method = "onBelowWorld()V", at = @At("HEAD"), cancellable = true)
    private void CancelVoidDamage(CallbackInfo ci)
    {
        LivingEntity livingEntity = (LivingEntity) (Object) this;
        if(livingEntity instanceof Player player)
        {
            if(player.isCreative()) ci.cancel();
            if(player.isSpectator()) ci.cancel();
        }
    }
}
