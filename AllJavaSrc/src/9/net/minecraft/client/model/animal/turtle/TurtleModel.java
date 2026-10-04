package net.minecraft.client.model.animal.turtle;

import java.util.Set;
import net.minecraft.client.model.BabyModelTransform;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.TurtleRenderState;
import net.minecraft.util.Mth;

public class TurtleModel extends QuadrupedModel<TurtleRenderState> {
   private static final String EGG_BELLY = "egg_belly";
   public static final MeshTransformer BABY_TRANSFORMER = new BabyModelTransform(true, 120.0F, 0.0F, 9.0F, 6.0F, 120.0F, Set.of("head"));
   private final ModelPart eggBelly;

   public TurtleModel(final ModelPart root) {
      super(root);
      this.eggBelly = root.getChild("egg_belly");
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(3, 0).addBox(-3.0F, -1.0F, -3.0F, 6.0F, 5.0F, 6.0F), PartPose.offset(0.0F, 19.0F, -10.0F));
      root.addOrReplaceChild(
         "body",
         CubeListBuilder.create()
            .texOffs(7, 37)
            .addBox("shell", -9.5F, 3.0F, -10.0F, 19.0F, 20.0F, 6.0F)
            .texOffs(31, 1)
            .addBox("belly", -5.5F, 3.0F, -13.0F, 11.0F, 18.0F, 3.0F),
         PartPose.offsetAndRotation(0.0F, 11.0F, -10.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
      );
      root.addOrReplaceChild(
         "egg_belly",
         CubeListBuilder.create().texOffs(70, 33).addBox(-4.5F, 3.0F, -14.0F, 9.0F, 18.0F, 1.0F),
         PartPose.offsetAndRotation(0.0F, 11.0F, -10.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
      );
      int legHeight = 1;
      root.addOrReplaceChild(
         "right_hind_leg", CubeListBuilder.create().texOffs(1, 23).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 1.0F, 10.0F), PartPose.offset(-3.5F, 22.0F, 11.0F)
      );
      root.addOrReplaceChild(
         "left_hind_leg", CubeListBuilder.create().texOffs(1, 12).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 1.0F, 10.0F), PartPose.offset(3.5F, 22.0F, 11.0F)
      );
      root.addOrReplaceChild(
         "right_front_leg", CubeListBuilder.create().texOffs(27, 30).addBox(-13.0F, 0.0F, -2.0F, 13.0F, 1.0F, 5.0F), PartPose.offset(-5.0F, 21.0F, -4.0F)
      );
      root.addOrReplaceChild(
         "left_front_leg", CubeListBuilder.create().texOffs(27, 24).addBox(0.0F, 0.0F, -2.0F, 13.0F, 1.0F, 5.0F), PartPose.offset(5.0F, 21.0F, -4.0F)
      );
      return LayerDefinition.create(mesh, 128, 64);
   }

   public void setupAnim(final TurtleRenderState state) {
      super.setupAnim(state);
      float animationPos = state.walkAnimationPos;
      float animationSpeed = state.walkAnimationSpeed;
      if (state.isOnLand) {
         float layEgg = state.isLayingEgg ? 4.0F : 1.0F;
         float layEggAmplitude = state.isLayingEgg ? 2.0F : 1.0F;
         float swingPos = animationPos * 5.0F;
         float frontSwing = Mth.cos(layEgg * swingPos);
         float hindSwing = Mth.cos(swingPos);
         this.rightFrontLeg.yRot = -frontSwing * 8.0F * animationSpeed * layEggAmplitude;
         this.leftFrontLeg.yRot = frontSwing * 8.0F * animationSpeed * layEggAmplitude;
         this.rightHindLeg.yRot = -hindSwing * 3.0F * animationSpeed;
         this.leftHindLeg.yRot = hindSwing * 3.0F * animationSpeed;
      } else {
         float swingScale = 0.5F * animationSpeed;
         float swing = Mth.cos(animationPos * 0.6662F * 0.6F) * swingScale;
         this.rightHindLeg.xRot = swing;
         this.leftHindLeg.xRot = -swing;
         this.rightFrontLeg.zRot = -swing;
         this.leftFrontLeg.zRot = swing;
      }

      this.eggBelly.visible = state.hasEgg;
      if (this.eggBelly.visible) {
         this.root.y--;
      }
   }
}
