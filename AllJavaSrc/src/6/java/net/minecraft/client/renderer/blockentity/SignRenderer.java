package net.minecraft.client.renderer.blockentity;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.Vec3;

public class SignRenderer extends AbstractSignRenderer {
   public static final float RENDER_SCALE = 0.6666667F;
   private static final Vec3 TEXT_OFFSET = new Vec3(0.0, 0.33333334F, 0.046666667F);
   private final Map<WoodType, SignRenderer.Models> signModels;

   public SignRenderer(final BlockEntityRendererProvider.Context context) {
      super(context);
      this.signModels = WoodType.values()
         .collect(
            ImmutableMap.toImmutableMap(
               type -> type,
               type -> new SignRenderer.Models(createSignModel(context.entityModelSet(), type, true), createSignModel(context.entityModelSet(), type, false))
            )
         );
   }

   @Override
   protected Model.Simple getSignModel(final BlockState blockState, final WoodType type) {
      SignRenderer.Models models = this.signModels.get(type);
      return blockState.getBlock() instanceof StandingSignBlock ? models.standing() : models.wall();
   }

   @Override
   protected Material getSignMaterial(final WoodType type) {
      return Sheets.getSignMaterial(type);
   }

   @Override
   protected float getSignModelRenderScale() {
      return 0.6666667F;
   }

   @Override
   protected float getSignTextRenderScale() {
      return 0.6666667F;
   }

   private static void translateBase(final PoseStack poseStack, final float angle) {
      poseStack.translate(0.5F, 0.5F, 0.5F);
      poseStack.mulPose(Axis.YP.rotationDegrees(angle));
   }

   @Override
   protected void translateSign(final PoseStack poseStack, final float angle, final BlockState blockState) {
      translateBase(poseStack, angle);
      if (!(blockState.getBlock() instanceof StandingSignBlock)) {
         poseStack.translate(0.0F, -0.3125F, -0.4375F);
      }
   }

   @Override
   protected Vec3 getTextOffset() {
      return TEXT_OFFSET;
   }

   public static void submitSpecial(
      final MaterialSet materials,
      final PoseStack poseStack,
      final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      final int overlayCoords,
      final Model.Simple model,
      final Material material
   ) {
      poseStack.pushPose();
      applyInHandTransforms(poseStack);
      submitNodeCollector.submitModel(
         model, Unit.INSTANCE, poseStack, material.renderType(model::renderType), lightCoords, overlayCoords, -1, materials.get(material), 0, null
      );
      poseStack.popPose();
   }

   public static void applyInHandTransforms(final PoseStack poseStack) {
      translateBase(poseStack, 0.0F);
      poseStack.scale(0.6666667F, -0.6666667F, -0.6666667F);
   }

   public static Model.Simple createSignModel(final EntityModelSet entityModelSet, final WoodType woodType, final boolean standing) {
      ModelLayerLocation layer = standing ? ModelLayers.createStandingSignModelName(woodType) : ModelLayers.createWallSignModelName(woodType);
      return new Model.Simple(entityModelSet.bakeLayer(layer), RenderTypes::entityCutoutNoCull);
   }

   public static LayerDefinition createSignLayer(final boolean standing) {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      root.addOrReplaceChild("sign", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -14.0F, -1.0F, 24.0F, 12.0F, 2.0F), PartPose.ZERO);
      if (standing) {
         root.addOrReplaceChild("stick", CubeListBuilder.create().texOffs(0, 14).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 14.0F, 2.0F), PartPose.ZERO);
      }

      return LayerDefinition.create(mesh, 64, 32);
   }

   private record Models(Model.Simple standing, Model.Simple wall) {
   }
}
