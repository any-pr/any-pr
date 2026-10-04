package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.core.Direction;
import net.minecraft.util.SpecialDates;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ChestRenderer<T extends BlockEntity & LidBlockEntity> implements BlockEntityRenderer<T, ChestRenderState> {
   private final MaterialSet materials;
   private final ChestModel singleModel;
   private final ChestModel doubleLeftModel;
   private final ChestModel doubleRightModel;
   private final boolean xmasTextures;

   public ChestRenderer(final BlockEntityRendererProvider.Context context) {
      this.materials = context.materials();
      this.xmasTextures = xmasTextures();
      this.singleModel = new ChestModel(context.bakeLayer(ModelLayers.CHEST));
      this.doubleLeftModel = new ChestModel(context.bakeLayer(ModelLayers.DOUBLE_CHEST_LEFT));
      this.doubleRightModel = new ChestModel(context.bakeLayer(ModelLayers.DOUBLE_CHEST_RIGHT));
   }

   public static boolean xmasTextures() {
      return SpecialDates.isExtendedChristmas();
   }

   public ChestRenderState createRenderState() {
      return new ChestRenderState();
   }

   public void extractRenderState(
      final T blockEntity,
      final ChestRenderState state,
      final float partialTicks,
      final Vec3 cameraPosition,
      final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
   ) {
      BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
      boolean hasLevel = blockEntity.getLevel() != null;
      BlockState blockState = hasLevel ? blockEntity.getBlockState() : Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
      state.type = blockState.hasProperty(ChestBlock.TYPE) ? blockState.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
      state.angle = blockState.getValue(ChestBlock.FACING).toYRot();
      state.material = this.getChestMaterial(blockEntity, this.xmasTextures);
      DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity> combineResult;
      if (hasLevel && blockState.getBlock() instanceof ChestBlock chestBlock) {
         combineResult = chestBlock.combine(blockState, blockEntity.getLevel(), blockEntity.getBlockPos(), true);
      } else {
         combineResult = DoubleBlockCombiner.Combiner::acceptNone;
      }

      state.open = combineResult.apply(ChestBlock.opennessCombiner(blockEntity)).get(partialTicks);
      if (state.type != ChestType.SINGLE) {
         state.lightCoords = combineResult.apply(new BrightnessCombiner<>()).applyAsInt(state.lightCoords);
      }
   }

   public void submit(final ChestRenderState state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
      poseStack.pushPose();
      poseStack.translate(0.5F, 0.5F, 0.5F);
      poseStack.mulPose(Axis.YP.rotationDegrees(-state.angle));
      poseStack.translate(-0.5F, -0.5F, -0.5F);
      float open = state.open;
      open = 1.0F - open;
      open = 1.0F - open * open * open;
      Material material = Sheets.chooseMaterial(state.material, state.type);
      RenderType renderType = material.renderType(RenderTypes::entityCutout);
      TextureAtlasSprite sprite = this.materials.get(material);
      if (state.type != ChestType.SINGLE) {
         if (state.type == ChestType.LEFT) {
            submitNodeCollector.submitModel(
               this.doubleLeftModel, open, poseStack, renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, sprite, 0, state.breakProgress
            );
         } else {
            submitNodeCollector.submitModel(
               this.doubleRightModel, open, poseStack, renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, sprite, 0, state.breakProgress
            );
         }
      } else {
         submitNodeCollector.submitModel(
            this.singleModel, open, poseStack, renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, sprite, 0, state.breakProgress
         );
      }

      poseStack.popPose();
   }

   private ChestRenderState.ChestMaterialType getChestMaterial(final BlockEntity entity, final boolean xmasTextures) {
      if (entity instanceof EnderChestBlockEntity) {
         return ChestRenderState.ChestMaterialType.ENDER_CHEST;
      }

      if (xmasTextures) {
         return ChestRenderState.ChestMaterialType.CHRISTMAS;
      }

      if (entity instanceof TrappedChestBlockEntity) {
         return ChestRenderState.ChestMaterialType.TRAPPED;
      }

      if (entity.getBlockState().getBlock() instanceof CopperChestBlock copperChestBlock) {
         return switch (copperChestBlock.getState()) {
            case UNAFFECTED -> ChestRenderState.ChestMaterialType.COPPER_UNAFFECTED;
            case EXPOSED -> ChestRenderState.ChestMaterialType.COPPER_EXPOSED;
            case WEATHERED -> ChestRenderState.ChestMaterialType.COPPER_WEATHERED;
            case OXIDIZED -> ChestRenderState.ChestMaterialType.COPPER_OXIDIZED;
         };
      } else {
         return ChestRenderState.ChestMaterialType.REGULAR;
      }
   }
}
