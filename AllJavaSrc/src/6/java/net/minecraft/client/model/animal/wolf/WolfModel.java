package net.minecraft.client.model.animal.wolf;

import java.util.Set;
import net.minecraft.client.model.BabyModelTransform;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.util.Mth;

public class WolfModel extends EntityModel<WolfRenderState> {
   public static final MeshTransformer BABY_TRANSFORMER = new BabyModelTransform(Set.of("head"));
   private static final String REAL_HEAD = "real_head";
   private static final String UPPER_BODY = "upper_body";
   private static final String REAL_TAIL = "real_tail";
   private final ModelPart head;
   private final ModelPart realHead;
   private final ModelPart body;
   private final ModelPart rightHindLeg;
   private final ModelPart leftHindLeg;
   private final ModelPart rightFrontLeg;
   private final ModelPart leftFrontLeg;
   private final ModelPart tail;
   private final ModelPart realTail;
   private final ModelPart upperBody;
   private static final int LEG_SIZE = 8;

   public WolfModel(final ModelPart root) {
      super(root);
      this.head = root.getChild("head");
      this.realHead = this.head.getChild("real_head");
      this.body = root.getChild("body");
      this.upperBody = root.getChild("upper_body");
      this.rightHindLeg = root.getChild("right_hind_leg");
      this.leftHindLeg = root.getChild("left_hind_leg");
      this.rightFrontLeg = root.getChild("right_front_leg");
      this.leftFrontLeg = root.getChild("left_front_leg");
      this.tail = root.getChild("tail");
      this.realTail = this.tail.getChild("real_tail");
   }

   public static MeshDefinition createMeshDefinition(final CubeDeformation g) {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      float headHeight = 13.5F;
      PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(-1.0F, 13.5F, -7.0F));
      head.addOrReplaceChild(
         "real_head",
         CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-2.0F, -3.0F, -2.0F, 6.0F, 6.0F, 4.0F, g)
            .texOffs(16, 14)
            .addBox(-2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F, g)
            .texOffs(16, 14)
            .addBox(2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F, g)
            .texOffs(0, 10)
            .addBox(-0.5F, -0.001F, -5.0F, 3.0F, 3.0F, 4.0F, g),
         PartPose.ZERO
      );
      root.addOrReplaceChild(
         "body",
         CubeListBuilder.create().texOffs(18, 14).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 9.0F, 6.0F, g),
         PartPose.offsetAndRotation(0.0F, 14.0F, 2.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
      );
      root.addOrReplaceChild(
         "upper_body",
         CubeListBuilder.create().texOffs(21, 0).addBox(-3.0F, -3.0F, -3.0F, 8.0F, 6.0F, 7.0F, g),
         PartPose.offsetAndRotation(-1.0F, 14.0F, -3.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
      );
      CubeListBuilder leftLeg = CubeListBuilder.create().texOffs(0, 18).addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, g);
      CubeListBuilder rightLeg = CubeListBuilder.create().mirror().texOffs(0, 18).addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, g);
      root.addOrReplaceChild("right_hind_leg", rightLeg, PartPose.offset(-2.5F, 16.0F, 7.0F));
      root.addOrReplaceChild("left_hind_leg", leftLeg, PartPose.offset(0.5F, 16.0F, 7.0F));
      root.addOrReplaceChild("right_front_leg", rightLeg, PartPose.offset(-2.5F, 16.0F, -4.0F));
      root.addOrReplaceChild("left_front_leg", leftLeg, PartPose.offset(0.5F, 16.0F, -4.0F));
      PartDefinition tail = root.addOrReplaceChild(
         "tail", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.0F, 12.0F, 8.0F, (float) (Math.PI / 5), 0.0F, 0.0F)
      );
      tail.addOrReplaceChild("real_tail", CubeListBuilder.create().texOffs(9, 18).addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, g), PartPose.ZERO);
      return mesh;
   }

   public void setupAnim(final WolfRenderState state) {
      super.setupAnim(state);
      float animationPos = state.walkAnimationPos;
      float animationSpeed = state.walkAnimationSpeed;
      if (state.isAngry) {
         this.tail.yRot = 0.0F;
      } else {
         this.tail.yRot = Mth.cos(animationPos * 0.6662F) * 1.4F * animationSpeed;
      }

      if (state.isSitting) {
         float ageScale = state.ageScale;
         this.upperBody.y += 2.0F * ageScale;
         this.upperBody.xRot = (float) (Math.PI * 2.0 / 5.0);
         this.upperBody.yRot = 0.0F;
         this.body.y += 4.0F * ageScale;
         this.body.z -= 2.0F * ageScale;
         this.body.xRot = (float) (Math.PI / 4);
         this.tail.y += 9.0F * ageScale;
         this.tail.z -= 2.0F * ageScale;
         this.rightHindLeg.y += 6.7F * ageScale;
         this.rightHindLeg.z -= 5.0F * ageScale;
         this.rightHindLeg.xRot = (float) (Math.PI * 3.0 / 2.0);
         this.leftHindLeg.y += 6.7F * ageScale;
         this.leftHindLeg.z -= 5.0F * ageScale;
         this.leftHindLeg.xRot = (float) (Math.PI * 3.0 / 2.0);
         this.rightFrontLeg.xRot = 5.811947F;
         this.rightFrontLeg.x += 0.01F * ageScale;
         this.rightFrontLeg.y += 1.0F * ageScale;
         this.leftFrontLeg.xRot = 5.811947F;
         this.leftFrontLeg.x -= 0.01F * ageScale;
         this.leftFrontLeg.y += 1.0F * ageScale;
      } else {
         this.rightHindLeg.xRot = Mth.cos(animationPos * 0.6662F) * 1.4F * animationSpeed;
         this.leftHindLeg.xRot = Mth.cos(animationPos * 0.6662F + (float) Math.PI) * 1.4F * animationSpeed;
         this.rightFrontLeg.xRot = Mth.cos(animationPos * 0.6662F + (float) Math.PI) * 1.4F * animationSpeed;
         this.leftFrontLeg.xRot = Mth.cos(animationPos * 0.6662F) * 1.4F * animationSpeed;
      }

      this.realHead.zRot = state.headRollAngle + state.getBodyRollAngle(0.0F);
      this.upperBody.zRot = state.getBodyRollAngle(-0.08F);
      this.body.zRot = state.getBodyRollAngle(-0.16F);
      this.realTail.zRot = state.getBodyRollAngle(-0.2F);
      this.head.xRot = state.xRot * (float) (Math.PI / 180.0);
      this.head.yRot = state.yRot * (float) (Math.PI / 180.0);
      this.tail.xRot = state.tailAngle;
   }
}
