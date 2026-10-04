package net.minecraft.client.renderer.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

public class MovingBlockRenderState implements BlockAndTintGetter {
   public BlockPos randomSeedPos = BlockPos.ZERO;
   public BlockPos blockPos = BlockPos.ZERO;
   public BlockState blockState = Blocks.AIR.defaultBlockState();
   public @Nullable Holder<Biome> biome;
   public BlockAndTintGetter level = EmptyBlockAndTintGetter.INSTANCE;

   @Override
   public float getShade(final Direction direction, final boolean shade) {
      return this.level.getShade(direction, shade);
   }

   @Override
   public LevelLightEngine getLightEngine() {
      return this.level.getLightEngine();
   }

   @Override
   public int getBlockTint(final BlockPos pos, final ColorResolver color) {
      return this.biome == null ? -1 : color.getColor(this.biome.value(), pos.getX(), pos.getZ());
   }

   @Override
   public @Nullable BlockEntity getBlockEntity(final BlockPos pos) {
      return null;
   }

   @Override
   public BlockState getBlockState(final BlockPos pos) {
      return pos.equals(this.blockPos) ? this.blockState : Blocks.AIR.defaultBlockState();
   }

   @Override
   public FluidState getFluidState(final BlockPos pos) {
      return this.getBlockState(pos).getFluidState();
   }

   @Override
   public int getHeight() {
      return 1;
   }

   @Override
   public int getMinY() {
      return this.blockPos.getY();
   }
}
