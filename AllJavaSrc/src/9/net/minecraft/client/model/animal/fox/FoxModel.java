package net.minecraft.client.model.animal.fox;

import java.util.Set;
import net.minecraft.client.model.BabyModelTransform;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.FoxRenderState;
import net.minecraft.util.Mth;

public class FoxModel extends EntityModel<FoxRenderState> {
   public static final MeshTransformer BABY_TRANSFORMER = new BabyModelTransform(true, 8.0F, 3.35F, Set.of("head"));
   public final ModelPart head;
   private final ModelPart body;
   private final ModelPart rightHindLeg;
   private final ModelPart leftHindLeg;
   private final ModelPart rightFrontLeg;
   private final ModelPart leftFrontLeg;
   private final ModelPart tail;
   private static final int LEG_SIZE = 6;
   private static final float HEAD_HEIGHT = 16.5F;
   private static final float LEG_POS = 17.5F;
   private float legMotionPos;

   public FoxModel(final ModelPart root) {
      super(root);
      this.head = root.getChild("head");
      this.body = root.getChild("body");
      this.rightHindLeg = root.getChild("right_hind_leg");
      this.leftHindLeg = root.getChild("left_hind_leg");
      this.rightFrontLeg = root.getChild("right_front_leg");
      this.leftFrontLeg = root.getChild("left_front_leg");
      this.tail = this.body.getChild("tail");
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.addOrReplaceChild(
         "head", CubeListBuilder.create().texOffs(1, 5).addBox(-3.0F, -2.0F, -5.0F, 8.0F, 6.0F, 6.0F), PartPose.offset(-1.0F, 16.5F, -3.0F)
      );
      head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(8, 1).addBox(-3.0F, -4.0F, -4.0F, 2.0F, 2.0F, 1.0F), PartPose.ZERO);
      head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(15, 1).addBox(3.0F, -4.0F, -4.0F, 2.0F, 2.0F, 1.0F), PartPose.ZERO);
      head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(6, 18).addBox(-1.0F, 2.01F, -8.0F, 4.0F, 2.0F, 3.0F), PartPose.ZERO);
      PartDefinition body = root.addOrReplaceChild(
         "body",
         CubeListBuilder.create().texOffs(24, 15).addBox(-3.0F, 3.999F, -3.5F, 6.0F, 11.0F, 6.0F),
         PartPose.offsetAndRotation(0.0F, 16.0F, -6.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
      );
      CubeDeformation fudge = new CubeDeformation(0.001F);
      CubeListBuilder leftLeg = CubeListBuilder.create().texOffs(4, 24).addBox(2.0F, 0.5F, -1.0F, 2.0F, 6.0F, 2.0F, fudge);
      CubeListBuilder rightLeg = CubeListBuilder.create().texOffs(13, 24).addBox(2.0F, 0.5F, -1.0F, 2.0F, 6.0F, 2.0F, fudge);
      root.addOrReplaceChild("right_hind_leg", rightLeg, PartPose.offset(-5.0F, 17.5F, 7.0F));
      root.addOrReplaceChild("left_hind_leg", leftLeg, PartPose.offset(-1.0F, 17.5F, 7.0F));
      root.addOrReplaceChild("right_front_leg", rightLeg, PartPose.offset(-5.0F, 17.5F, 0.0F));
      root.addOrReplaceChild("left_front_leg", leftLeg, PartPose.offset(-1.0F, 17.5F, 0.0F));
      body.addOrReplaceChild(
         "tail",
         CubeListBuilder.create().texOffs(30, 0).addBox(2.0F, 0.0F, -1.0F, 4.0F, 9.0F, 5.0F),
         PartPose.offsetAndRotation(-4.0F, 15.0F, -1.0F, -0.05235988F, 0.0F, 0.0F)
      );
      return LayerDefinition.create(mesh, 48, 32);
   }

   public void setupAnim(final FoxRenderState state) {
      super.setupAnim(state);
      float animationSpeed = state.walkAnimationSpeed;
      float animationPos = state.walkAnimationPos;
      this.rightHindLeg.xRot = Mth.cos(animationPos * 0.6662F) * 1.4F * animationSpeed;
      this.leftHindLeg.xRot = Mth.cos(animationPos * 0.6662F + (float) Math.PI) * 1.4F * animationSpeed;
      this.rightFrontLeg.xRot = Mth.cos(animationPos * 0.6662F + (float) Math.PI) * 1.4F * animationSpeed;
      this.leftFrontLeg.xRot = Mth.cos(animationPos * 0.6662F) * 1.4F * animationSpeed;
      this.head.zRot = state.headRollAngle;
      this.rightHindLeg.visible = true;
      this.leftHindLeg.visible = true;
      this.rightFrontLeg.visible = true;
      this.leftFrontLeg.visible = true;
      float ageScale = state.ageScale;
      if (state.isCrouching) {
         this.body.xRot += 0.10471976F;
         float crouch = state.crouchAmount;
         this.body.y += crouch * ageScale;
         this.head.y += crouch * ageScale;
      } else if (state.isSleeping) {
         this.body.zRot = (float) (-Math.PI / 2);
         this.body.y += 5.0F * ageScale;
         this.tail.xRot = (float) (-Math.PI * 5.0 / 6.0);
         if (state.isBaby) {
            this.tail.xRot = -2.1816616F;
            this.body.z += 2.0F;
         }

         this.head.x += 2.0F * ageScale;
         this.head.y += 2.99F * ageScale;
         this.head.yRot = (float) (-Math.PI * 2.0 / 3.0);
         this.head.zRot = 0.0F;
         this.rightHindLeg.visible = false;
         this.leftHindLeg.visible = false;
         this.rightFrontLeg.visible = false;
         this.leftFrontLeg.visible = false;
      } else if (state.isSitting) {
         this.body.xRot = (float) (Math.PI / 6);
         this.body.y -= 7.0F * ageScale;
         this.body.z += 3.0F * ageScale;
         this.tail.xRot = (float) (Math.PI / 4);
         this.tail.z -= 1.0F * ageScale;
         this.head.xRot = 0.0F;
         this.head.yRot = 0.0F;
         if (state.isBaby) {
            this.head.y -= 1.75F;
            this.head.z -= 0.375F;
         } else {
            this.head.y -= 6.5F;
            this.head.z += 2.75F;
         }

         this.rightHindLeg.xRot = (float) (-Math.PI * 5.0 / 12.0);
         this.rightHindLeg.y += 4.0F * ageScale;
         this.rightHindLeg.z -= 0.25F * ageScale;
         this.leftHindLeg.xRot = (float) (-Math.PI * 5.0 / 12.0);
         this.leftHindLeg.y += 4.0F * ageScale;
         this.leftHindLeg.z -= 0.25F * ageScale;
         this.rightFrontLeg.xRot = (float) (-Math.PI / 12);
         this.leftFrontLeg.xRot = (float) (-Math.PI / 12);
      }

      if (!state.isSleeping && !state.isFaceplanted && !state.isCrouching) {
         this.head.xRot = state.xRot * (float) (Math.PI / 180.0);
         this.head.yRot = state.yRot * (float) (Math.PI / 180.0);
      }

      if (state.isSleeping) {
         this.head.xRot = 0.0F;
         this.head.yRot = (float) (-Math.PI * 2.0 / 3.0);
         this.head.zRot = Mth.cos(state.ageInTicks * 0.027F) / 22.0F;
      }

      if (state.isCrouching) {
         float wiggleAmount = Mth.cos(state.ageInTicks) * 0.01F;
         this.body.yRot = wiggleAmount;
         this.rightHindLeg.zRot = wiggleAmount;
         this.leftHindLeg.zRot = wiggleAmount;
         this.rightFrontLeg.zRot = wiggleAmount / 2.0F;
         this.leftFrontLeg.zRot = wiggleAmount / 2.0F;
      }

      if (state.isFaceplanted) {
         float legMoveFactor = 0.1F;
         this.legMotionPos += 0.67F;
         this.rightHindLeg.xRot = Mth.cos(this.legMotionPos * 0.4662F) * 0.1F;
         this.leftHindLeg.xRot = Mth.cos(this.legMotionPos * 0.4662F + (float) Math.PI) * 0.1F;
         this.rightFrontLeg.xRot = Mth.cos(this.legMotionPos * 0.4662F + (float) Math.PI) * 0.1F;
         this.leftFrontLeg.xRot = Mth.cos(this.legMotionPos * 0.4662F) * 0.1F;
      }
   }
}
