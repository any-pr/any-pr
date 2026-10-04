package net.minecraft.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.SwingAnimationType;

public class AnimationUtils {
   public static void animateCrossbowHold(final ModelPart rightArm, final ModelPart leftArm, final ModelPart head, final boolean holdingInRightArm) {
      ModelPart holdingArm = holdingInRightArm ? rightArm : leftArm;
      ModelPart shootingArm = holdingInRightArm ? leftArm : rightArm;
      holdingArm.yRot = (holdingInRightArm ? -0.3F : 0.3F) + head.yRot;
      shootingArm.yRot = (holdingInRightArm ? 0.6F : -0.6F) + head.yRot;
      holdingArm.xRot = (float) (-Math.PI / 2) + head.xRot + 0.1F;
      shootingArm.xRot = -1.5F + head.xRot;
   }

   public static void animateCrossbowCharge(
      final ModelPart rightArm, final ModelPart leftArm, final float maxCrossbowChargeDuration, final float ticksUsingItem, final boolean holdingInRightArm
   ) {
      ModelPart holdingArm = holdingInRightArm ? rightArm : leftArm;
      ModelPart pullingArm = holdingInRightArm ? leftArm : rightArm;
      holdingArm.yRot = holdingInRightArm ? -0.8F : 0.8F;
      holdingArm.xRot = -0.97079635F;
      pullingArm.xRot = holdingArm.xRot;
      float useTicks = Mth.clamp(ticksUsingItem, 0.0F, maxCrossbowChargeDuration);
      float lerpAlpha = useTicks / maxCrossbowChargeDuration;
      pullingArm.yRot = Mth.lerp(lerpAlpha, 0.4F, 0.85F) * (holdingInRightArm ? 1 : -1);
      pullingArm.xRot = Mth.lerp(lerpAlpha, pullingArm.xRot, (float) (-Math.PI / 2));
   }

   public static void swingWeaponDown(
      final ModelPart rightArm, final ModelPart leftArm, final HumanoidArm mainArm, final float attackTime, final float ageInTicks
   ) {
      float attack2 = Mth.sin(attackTime * (float) Math.PI);
      float attack = Mth.sin((1.0F - (1.0F - attackTime) * (1.0F - attackTime)) * (float) Math.PI);
      rightArm.zRot = 0.0F;
      leftArm.zRot = 0.0F;
      rightArm.yRot = (float) (Math.PI / 20);
      leftArm.yRot = (float) (-Math.PI / 20);
      if (mainArm == HumanoidArm.RIGHT) {
         rightArm.xRot = -1.8849558F + Mth.cos(ageInTicks * 0.09F) * 0.15F;
         leftArm.xRot = -0.0F + Mth.cos(ageInTicks * 0.19F) * 0.5F;
         rightArm.xRot += attack2 * 2.2F - attack * 0.4F;
         leftArm.xRot += attack2 * 1.2F - attack * 0.4F;
      } else {
         rightArm.xRot = -0.0F + Mth.cos(ageInTicks * 0.19F) * 0.5F;
         leftArm.xRot = -1.8849558F + Mth.cos(ageInTicks * 0.09F) * 0.15F;
         rightArm.xRot += attack2 * 1.2F - attack * 0.4F;
         leftArm.xRot += attack2 * 2.2F - attack * 0.4F;
      }

      bobArms(rightArm, leftArm, ageInTicks);
   }

   public static void bobModelPart(final ModelPart modelPart, final float ageInTicks, final float scale) {
      modelPart.zRot = modelPart.zRot + scale * (Mth.cos(ageInTicks * 0.09F) * 0.05F + 0.05F);
      modelPart.xRot = modelPart.xRot + scale * (Mth.sin(ageInTicks * 0.067F) * 0.05F);
   }

   public static void bobArms(final ModelPart rightArm, final ModelPart leftArm, final float ageInTicks) {
      bobModelPart(rightArm, ageInTicks, 1.0F);
      bobModelPart(leftArm, ageInTicks, -1.0F);
   }

   public static <T extends UndeadRenderState> void animateZombieArms(
      final ModelPart leftArm, final ModelPart rightArm, final boolean aggressive, final T state
   ) {
      boolean animateAttack = state.swingAnimationType != SwingAnimationType.STAB;
      if (animateAttack) {
         float attackTime = state.attackTime;
         float armDrop = (float) -Math.PI / (aggressive ? 1.5F : 2.25F);
         float attackYRotModifier = Mth.sin(attackTime * (float) Math.PI);
         float attackXRotModifier = Mth.sin((1.0F - (1.0F - attackTime) * (1.0F - attackTime)) * (float) Math.PI);
         rightArm.zRot = 0.0F;
         rightArm.yRot = -(0.1F - attackYRotModifier * 0.6F);
         rightArm.xRot = armDrop;
         rightArm.xRot += attackYRotModifier * 1.2F - attackXRotModifier * 0.4F;
         leftArm.zRot = 0.0F;
         leftArm.yRot = 0.1F - attackYRotModifier * 0.6F;
         leftArm.xRot = armDrop;
         leftArm.xRot += attackYRotModifier * 1.2F - attackXRotModifier * 0.4F;
      }

      bobArms(rightArm, leftArm, state.ageInTicks);
   }
}
