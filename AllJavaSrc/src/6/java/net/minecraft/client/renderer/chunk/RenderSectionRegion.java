package net.minecraft.client.renderer.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

public class RenderSectionRegion implements BlockAndTintGetter {
   public static final int RADIUS = 1;
   public static final int SIZE = 3;
   private final int minSectionX;
   private final int minSectionY;
   private final int minSectionZ;
   private final SectionCopy[] sections;
   private final Level level;

   RenderSectionRegion(final Level level, final int minSectionX, final int minSectionY, final int minSectionZ, final SectionCopy[] sections) {
      this.level = level;
      this.minSectionX = minSectionX;
      this.minSectionY = minSectionY;
      this.minSectionZ = minSectionZ;
      this.sections = sections;
   }

   @Override
   public BlockState getBlockState(final BlockPos pos) {
      return this.getSection(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ()))
         .getBlockState(pos);
   }

   @Override
   public FluidState getFluidState(final BlockPos pos) {
      return this.getSection(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ()))
         .getBlockState(pos)
         .getFluidState();
   }

   @Override
   public float getShade(final Direction direction, final boolean shade) {
      return this.level.getShade(direction, shade);
   }

   @Override
   public LevelLightEngine getLightEngine() {
      return this.level.getLightEngine();
   }

   @Override
   public @Nullable BlockEntity getBlockEntity(final BlockPos pos) {
      return this.getSection(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ()))
         .getBlockEntity(pos);
   }

   private SectionCopy getSection(final int sectionX, final int sectionY, final int sectionZ) {
      return this.sections[index(this.minSectionX, this.minSectionY, this.minSectionZ, sectionX, sectionY, sectionZ)];
   }

   @Override
   public int getBlockTint(final BlockPos pos, final ColorResolver resolver) {
      return this.level.getBlockTint(pos, resolver);
   }

   @Override
   public int getMinY() {
      return this.level.getMinY();
   }

   @Override
   public int getHeight() {
      return this.level.getHeight();
   }

   public static int index(final int minSectionX, final int minSectionY, final int minSectionZ, final int sectionX, final int sectionY, final int sectionZ) {
      return sectionX - minSectionX + (sectionY - minSectionY) * 3 + (sectionZ - minSectionZ) * 3 * 3;
   }
}
