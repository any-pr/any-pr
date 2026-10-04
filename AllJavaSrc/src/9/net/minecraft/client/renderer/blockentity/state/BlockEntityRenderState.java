package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.CrashReportCategory;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class BlockEntityRenderState {
   public BlockPos blockPos = BlockPos.ZERO;
   public BlockState blockState = Blocks.AIR.defaultBlockState();
   public BlockEntityType<?> blockEntityType = BlockEntityType.TEST_BLOCK;
   public int lightCoords;
   public ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress;

   public static void extractBase(
      final BlockEntity blockEntity, final BlockEntityRenderState state, final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
   ) {
      state.blockPos = blockEntity.getBlockPos();
      state.blockState = blockEntity.getBlockState();
      state.blockEntityType = blockEntity.getType();
      state.lightCoords = blockEntity.getLevel() != null ? LevelRenderer.getLightColor(blockEntity.getLevel(), blockEntity.getBlockPos()) : 15728880;
      state.breakProgress = breakProgress;
   }

   public void fillCrashReportCategory(final CrashReportCategory category) {
      category.setDetail("BlockEntityRenderState", this.getClass().getCanonicalName());
      category.setDetail("Position", this.blockPos);
      category.setDetail("Block state", this.blockState::toString);
   }
}
