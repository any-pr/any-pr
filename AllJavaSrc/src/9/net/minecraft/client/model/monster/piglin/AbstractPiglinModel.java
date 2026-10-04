package net.minecraft.client.model.monster.piglin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

public class AbstractPiglinModel<S extends HumanoidRenderState> extends HumanoidModel<S> {
   private static final String LEFT_SLEEVE = "left_sleeve";
   private static final String RIGHT_SLEEVE = "right_sleeve";
   private static final String LEFT_PANTS = "left_pants";
   private static final String RIGHT_PANTS = "right_pants";
   public final ModelPart leftSleeve = this.leftArm.getChild("left_sleeve");
   public final ModelPart rightSleeve = this.rightArm.getChild("right_sleeve");
   public final ModelPart leftPants = this.leftLeg.getChild("left_pants");
   public final ModelPart rightPants = this.rightLeg.getChild("right_pants");
   public final ModelPart jacket = this.body.getChild("jacket");
   public final ModelPart rightEar = this.head.getChild("right_ear");
   public final ModelPart leftEar = this.head.getChild("left_ear");

   public AbstractPiglinModel(final ModelPart root) {
      super(root, RenderTypes::entityTranslucent);
   }

   public static MeshDefinition createMesh(final CubeDeformation g) {
      MeshDefinition mesh = PlayerModel.createMesh(g, false);
      PartDefinition root = mesh.getRoot();
      root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, g), PartPose.ZERO);
      PartDefinition head = addHead(g, mesh);
      head.clearChild("hat");
      return mesh;
   }

   public static ArmorModelSet<MeshDefinition> createArmorMeshSet(final CubeDeformation innerDeformation, final CubeDeformation outerDeformation) {
      return PlayerModel.createArmorMeshSet(innerDeformation, outerDeformation).map(mesh -> {
         PartDefinition root = mesh.getRoot();
         PartDefinition head = root.getChild("head");
         head.addOrReplaceChild("left_ear", CubeListBuilder.create(), PartPose.ZERO);
         head.addOrReplaceChild("right_ear", CubeListBuilder.create(), PartPose.ZERO);
         return (MeshDefinition)mesh;
      });
   }

   public static PartDefinition addHead(final CubeDeformation g, final MeshDefinition mesh) {
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.addOrReplaceChild(
         "head",
         CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-5.0F, -8.0F, -4.0F, 10.0F, 8.0F, 8.0F, g)
            .texOffs(31, 1)
            .addBox(-2.0F, -4.0F, -5.0F, 4.0F, 4.0F, 1.0F, g)
            .texOffs(2, 4)
            .addBox(2.0F, -2.0F, -5.0F, 1.0F, 2.0F, 1.0F, g)
            .texOffs(2, 0)
            .addBox(-3.0F, -2.0F, -5.0F, 1.0F, 2.0F, 1.0F, g),
         PartPose.ZERO
      );
      head.addOrReplaceChild(
         "left_ear",
         CubeListBuilder.create().texOffs(51, 6).addBox(0.0F, 0.0F, -2.0F, 1.0F, 5.0F, 4.0F, g),
         PartPose.offsetAndRotation(4.5F, -6.0F, 0.0F, 0.0F, 0.0F, (float) (-Math.PI / 6))
      );
      head.addOrReplaceChild(
         "right_ear",
         CubeListBuilder.create().texOffs(39, 6).addBox(-1.0F, 0.0F, -2.0F, 1.0F, 5.0F, 4.0F, g),
         PartPose.offsetAndRotation(-4.5F, -6.0F, 0.0F, 0.0F, 0.0F, (float) (Math.PI / 6))
      );
      return head;
   }

   @Override
   public void setupAnim(final S state) {
      super.setupAnim(state);
      float animationPos = state.walkAnimationPos;
      float animationSpeed = state.walkAnimationSpeed;
      float defaultAngle = (float) (Math.PI / 6);
      float frequency = state.ageInTicks * 0.1F + animationPos * 0.5F;
      float amplitude = 0.08F + animationSpeed * 0.4F;
      this.leftEar.zRot = (float) (-Math.PI / 6) - Mth.cos(frequency * 1.2F) * amplitude;
      this.rightEar.zRot = (float) (Math.PI / 6) + Mth.cos(frequency) * amplitude;
   }

   @Override
   public void setAllVisible(final boolean visible) {
      super.setAllVisible(visible);
      this.leftSleeve.visible = visible;
      this.rightSleeve.visible = visible;
      this.leftPants.visible = visible;
      this.rightPants.visible = visible;
      this.jacket.visible = visible;
   }
}
