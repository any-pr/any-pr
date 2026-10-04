package primitiveenderdragon.briefrelease.importantbugfix.mixin;

import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Attributes.class)
public class AFRAttributes {

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/attributes/RangedAttribute;<init>(Ljava/lang/String;DDD)V",
                    ordinal = 0
            ),
            index = 3
    )
    private static double modifyArmorMax(double maxValue) {
        return 100.0;
    }

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/attributes/RangedAttribute;<init>(Ljava/lang/String;DDD)V",
                    ordinal = 1
            ),
            index = 3
    )
    private static double modifyArmorToughnessMax(double maxValue) {
        return 1024.0;
    }
}