package net.minecraft.world.level.levelgen.feature;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import net.minecraft.world.phys.AABB;

public class SpikeFeature extends Feature<SpikeConfiguration> {
   public static final int NUMBER_OF_SPIKES = 10;
   private static final int SPIKE_DISTANCE = 42;
   private static final LoadingCache<Long, List<SpikeFeature.EndSpike>> SPIKE_CACHE = CacheBuilder.newBuilder()
      .expireAfterWrite(5L, TimeUnit.MINUTES)
      .build(new SpikeFeature.SpikeCacheLoader());

   public SpikeFeature(final Codec<SpikeConfiguration> codec) {
      super(codec);
   }

   public static List<SpikeFeature.EndSpike> getSpikesForLevel(final WorldGenLevel level) {
      RandomSource random = RandomSource.create(level.getSeed());
      long key = random.nextLong() & 65535L;
      return (List<SpikeFeature.EndSpike>)SPIKE_CACHE.getUnchecked(key);
   }

   @Override
   public boolean place(final FeaturePlaceContext<SpikeConfiguration> context) {
      SpikeConfiguration config = context.config();
      WorldGenLevel level = context.level();
      RandomSource random = context.random();
      BlockPos origin = context.origin();
      List<SpikeFeature.EndSpike> spikes = config.getSpikes();
      if (spikes.isEmpty()) {
         spikes = getSpikesForLevel(level);
      }

      for (SpikeFeature.EndSpike spike : spikes) {
         if (spike.isCenterWithinChunk(origin)) {
            this.placeSpike(level, random, config, spike);
         }
      }

      return true;
   }

   private void placeSpike(final ServerLevelAccessor level, final RandomSource random, final SpikeConfiguration config, final SpikeFeature.EndSpike spike) {
      int radius = spike.getRadius();

      for (BlockPos pos : BlockPos.betweenClosed(
         new BlockPos(spike.getCenterX() - radius, level.getMinY(), spike.getCenterZ() - radius),
         new BlockPos(spike.getCenterX() + radius, spike.getHeight() + 10, spike.getCenterZ() + radius)
      )) {
         if (pos.distToLowCornerSqr(spike.getCenterX(), pos.getY(), spike.getCenterZ()) <= radius * radius + 1 && pos.getY() < spike.getHeight()) {
            this.setBlock(level, pos, Blocks.OBSIDIAN.defaultBlockState());
         } else if (pos.getY() > 65) {
            this.setBlock(level, pos, Blocks.AIR.defaultBlockState());
         }
      }

      if (spike.isGuarded()) {
         int start = -2;
         int end = 2;
         int yEnd = 3;
         BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               for (int dy = 0; dy <= 3; dy++) {
                  boolean isXSide = Mth.abs(dx) == 2;
                  boolean isZSide = Mth.abs(dz) == 2;
                  boolean top = dy == 3;
                  if (isXSide || isZSide || top) {
                     boolean xEdge = dx == -2 || dx == 2 || top;
                     boolean zEdge = dz == -2 || dz == 2 || top;
                     BlockState state = Blocks.IRON_BARS
                        .defaultBlockState()
                        .setValue(IronBarsBlock.NORTH, xEdge && dz != -2)
                        .setValue(IronBarsBlock.SOUTH, xEdge && dz != 2)
                        .setValue(IronBarsBlock.WEST, zEdge && dx != -2)
                        .setValue(IronBarsBlock.EAST, zEdge && dx != 2);
                     this.setBlock(level, pos.set(spike.getCenterX() + dx, spike.getHeight() + dy, spike.getCenterZ() + dz), state);
                  }
               }
            }
         }
      }

      EndCrystal endCrystal = EntityType.END_CRYSTAL.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
      if (endCrystal != null) {
         endCrystal.setBeamTarget(config.getCrystalBeamTarget());
         endCrystal.setInvulnerable(config.isCrystalInvulnerable());
         endCrystal.snapTo(spike.getCenterX() + 0.5, spike.getHeight() + 1, spike.getCenterZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
         level.addFreshEntity(endCrystal);
         BlockPos crystalPos = endCrystal.blockPosition();
         this.setBlock(level, crystalPos.below(), Blocks.BEDROCK.defaultBlockState());
         this.setBlock(level, crystalPos, FireBlock.getState(level, crystalPos));
      }
   }

   public static class EndSpike {
      public static final Codec<SpikeFeature.EndSpike> CODEC = RecordCodecBuilder.create(
         i -> i.group(
               Codec.INT.fieldOf("centerX").orElse(0).forGetter(s -> s.centerX),
               Codec.INT.fieldOf("centerZ").orElse(0).forGetter(s -> s.centerZ),
               Codec.INT.fieldOf("radius").orElse(0).forGetter(s -> s.radius),
               Codec.INT.fieldOf("height").orElse(0).forGetter(s -> s.height),
               Codec.BOOL.fieldOf("guarded").orElse(false).forGetter(s -> s.guarded)
            )
            .apply(i, SpikeFeature.EndSpike::new)
      );
      private final int centerX;
      private final int centerZ;
      private final int radius;
      private final int height;
      private final boolean guarded;
      private final AABB topBoundingBox;

      public EndSpike(final int centerX, final int centerZ, final int radius, final int height, final boolean guarded) {
         this.centerX = centerX;
         this.centerZ = centerZ;
         this.radius = radius;
         this.height = height;
         this.guarded = guarded;
         this.topBoundingBox = new AABB(centerX - radius, DimensionType.MIN_Y, centerZ - radius, centerX + radius, DimensionType.MAX_Y, centerZ + radius);
      }

      public boolean isCenterWithinChunk(final BlockPos chunkOrigin) {
         return SectionPos.blockToSectionCoord(chunkOrigin.getX()) == SectionPos.blockToSectionCoord(this.centerX)
            && SectionPos.blockToSectionCoord(chunkOrigin.getZ()) == SectionPos.blockToSectionCoord(this.centerZ);
      }

      public int getCenterX() {
         return this.centerX;
      }

      public int getCenterZ() {
         return this.centerZ;
      }

      public int getRadius() {
         return this.radius;
      }

      public int getHeight() {
         return this.height;
      }

      public boolean isGuarded() {
         return this.guarded;
      }

      public AABB getTopBoundingBox() {
         return this.topBoundingBox;
      }
   }

   private static class SpikeCacheLoader extends CacheLoader<Long, List<SpikeFeature.EndSpike>> {
      public List<SpikeFeature.EndSpike> load(final Long seed) {
         IntArrayList sizes = Util.toShuffledList(IntStream.range(0, 10), RandomSource.create(seed));
         List<SpikeFeature.EndSpike> result = Lists.newArrayList();

         for (int i = 0; i < 10; i++) {
            int x = Mth.floor(42.0 * Math.cos(2.0 * (-Math.PI + (Math.PI / 10) * i)));
            int z = Mth.floor(42.0 * Math.sin(2.0 * (-Math.PI + (Math.PI / 10) * i)));
            int size = sizes.get(i);
            int radius = 2 + size / 3;
            int height = 76 + size * 3;
            boolean guarded = size == 1 || size == 2;
            result.add(new SpikeFeature.EndSpike(x, z, radius, height, guarded));
         }

         return result;
      }
   }
}
