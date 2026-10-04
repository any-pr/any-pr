package com.smallmanseries.farlandstraveler.mixin.worldgen;

import com.smallmanseries.farlandstraveler.Config;
import com.smallmanseries.farlandstraveler.common.MathUtil;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Clamp")
public abstract class DensityFunctionsClampMixin {

    // 强制生成天空网格
    @Redirect(method = "transform", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D"))
    private static double clamp(double value, double min, double max) {
        return Config.FORCE_SKY_GRID.getAsBoolean() ? MathUtil.clamp(value, min, max) : Mth.clamp(value, min, max);
    }
}
