package net.minecraft.world.level.levelgen;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jspecify.annotations.Nullable;

public class SurfaceRules {
   public static final SurfaceRules.ConditionSource ON_FLOOR = stoneDepthCheck(0, false, CaveSurface.FLOOR);
   public static final SurfaceRules.ConditionSource UNDER_FLOOR = stoneDepthCheck(0, true, CaveSurface.FLOOR);
   public static final SurfaceRules.ConditionSource DEEP_UNDER_FLOOR = stoneDepthCheck(0, true, 6, CaveSurface.FLOOR);
   public static final SurfaceRules.ConditionSource VERY_DEEP_UNDER_FLOOR = stoneDepthCheck(0, true, 30, CaveSurface.FLOOR);
   public static final SurfaceRules.ConditionSource ON_CEILING = stoneDepthCheck(0, false, CaveSurface.CEILING);
   public static final SurfaceRules.ConditionSource UNDER_CEILING = stoneDepthCheck(0, true, CaveSurface.CEILING);

   public static SurfaceRules.ConditionSource stoneDepthCheck(final int offset, final boolean addSurfaceDepth1, final CaveSurface surfaceType) {
      return new SurfaceRules.StoneDepthCheck(offset, addSurfaceDepth1, 0, surfaceType);
   }

   public static SurfaceRules.ConditionSource stoneDepthCheck(
      final int offset, final boolean addSurfaceDepth1, final int secondaryDepthRange, final CaveSurface surfaceType
   ) {
      return new SurfaceRules.StoneDepthCheck(offset, addSurfaceDepth1, secondaryDepthRange, surfaceType);
   }

   public static SurfaceRules.ConditionSource not(final SurfaceRules.ConditionSource target) {
      return new SurfaceRules.NotConditionSource(target);
   }

   public static SurfaceRules.ConditionSource yBlockCheck(final VerticalAnchor anchor, final int surfaceDepthMultiplier) {
      return new SurfaceRules.YConditionSource(anchor, surfaceDepthMultiplier, false);
   }

   public static SurfaceRules.ConditionSource yStartCheck(final VerticalAnchor anchor, final int surfaceDepthMultiplier) {
      return new SurfaceRules.YConditionSource(anchor, surfaceDepthMultiplier, true);
   }

   public static SurfaceRules.ConditionSource waterBlockCheck(final int offset, final int surfaceDepthMultiplier) {
      return new SurfaceRules.WaterConditionSource(offset, surfaceDepthMultiplier, false);
   }

   public static SurfaceRules.ConditionSource waterStartCheck(final int offset, final int surfaceDepthMultiplier) {
      return new SurfaceRules.WaterConditionSource(offset, surfaceDepthMultiplier, true);
   }

   @SafeVarargs
   public static SurfaceRules.ConditionSource isBiome(final ResourceKey<Biome>... target) {
      return isBiome(List.of(target));
   }

   private static SurfaceRules.BiomeConditionSource isBiome(final List<ResourceKey<Biome>> target) {
      return new SurfaceRules.BiomeConditionSource(target);
   }

   public static SurfaceRules.ConditionSource noiseCondition(final ResourceKey<NormalNoise.NoiseParameters> noise, final double minRange) {
      return noiseCondition(noise, minRange, Double.MAX_VALUE);
   }

   public static SurfaceRules.ConditionSource noiseCondition(final ResourceKey<NormalNoise.NoiseParameters> noise, final double minRange, final double maxRange) {
      return new SurfaceRules.NoiseThresholdConditionSource(noise, minRange, maxRange);
   }

   public static SurfaceRules.ConditionSource verticalGradient(
      final String randomName, final VerticalAnchor trueAtAndBelow, final VerticalAnchor falseAtAndAbove
   ) {
      return new SurfaceRules.VerticalGradientConditionSource(Identifier.parse(randomName), trueAtAndBelow, falseAtAndAbove);
   }

   public static SurfaceRules.ConditionSource steep() {
      return SurfaceRules.Steep.INSTANCE;
   }

   public static SurfaceRules.ConditionSource hole() {
      return SurfaceRules.Hole.INSTANCE;
   }

   public static SurfaceRules.ConditionSource abovePreliminarySurface() {
      return SurfaceRules.AbovePreliminarySurface.INSTANCE;
   }

   public static SurfaceRules.ConditionSource temperature() {
      return SurfaceRules.Temperature.INSTANCE;
   }

   public static SurfaceRules.RuleSource ifTrue(final SurfaceRules.ConditionSource condition, final SurfaceRules.RuleSource next) {
      return new SurfaceRules.TestRuleSource(condition, next);
   }

   public static SurfaceRules.RuleSource sequence(final SurfaceRules.RuleSource... rules) {
      if (rules.length == 0) {
         throw new IllegalArgumentException("Need at least 1 rule for a sequence");
      } else {
         return new SurfaceRules.SequenceRuleSource(Arrays.asList(rules));
      }
   }

   public static SurfaceRules.RuleSource state(final BlockState state) {
      return new SurfaceRules.BlockRuleSource(state);
   }

   public static SurfaceRules.RuleSource bandlands() {
      return SurfaceRules.Bandlands.INSTANCE;
   }

   private static <A> MapCodec<? extends A> register(
      final Registry<MapCodec<? extends A>> registry, final String name, final KeyDispatchDataCodec<? extends A> codec
   ) {
      return Registry.register(registry, name, codec.codec());
   }

   private enum AbovePreliminarySurface implements SurfaceRules.ConditionSource {
      INSTANCE;

      private static final KeyDispatchDataCodec<SurfaceRules.AbovePreliminarySurface> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
         return CODEC;
      }

      public SurfaceRules.Condition apply(final SurfaceRules.Context context) {
         return context.abovePreliminarySurface;
      }
   }

   private enum Bandlands implements SurfaceRules.RuleSource {
      INSTANCE;

      private static final KeyDispatchDataCodec<SurfaceRules.Bandlands> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
         return CODEC;
      }

      public SurfaceRules.SurfaceRule apply(final SurfaceRules.Context context) {
         return context.system::getBand;
      }
   }

   private static final class BiomeConditionSource implements SurfaceRules.ConditionSource {
      private static final KeyDispatchDataCodec<SurfaceRules.BiomeConditionSource> CODEC = KeyDispatchDataCodec.of(
         ResourceKey.codec(Registries.BIOME).listOf().fieldOf("biome_is").xmap(SurfaceRules::isBiome, e -> e.biomes)
      );
      private final List<ResourceKey<Biome>> biomes;
      private final Predicate<ResourceKey<Biome>> biomeNameTest;

      private BiomeConditionSource(final List<ResourceKey<Biome>> biomes) {
         this.biomes = biomes;
         this.biomeNameTest = Set.copyOf(biomes)::contains;
      }

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
         return CODEC;
      }

      public SurfaceRules.Condition apply(final SurfaceRules.Context ruleContext) {
         class BiomeCondition extends SurfaceRules.LazyYCondition {
            private BiomeCondition() {
               super(ruleContext);
            }

            @Override
            protected boolean compute() {
               return this.context.biome.get().is(BiomeConditionSource.this.biomeNameTest);
            }
         }

         return new BiomeCondition();
      }

      @Override
      public boolean equals(final Object o) {
         if (this == o) {
            return true;
         } else {
            return o instanceof SurfaceRules.BiomeConditionSource that ? this.biomes.equals(that.biomes) : false;
         }
      }

      @Override
      public int hashCode() {
         return this.biomes.hashCode();
      }

      @Override
      public String toString() {
         return "BiomeConditionSource[biomes=" + this.biomes + "]";
      }
   }

   private record BlockRuleSource(BlockState resultState, SurfaceRules.StateRule rule) implements SurfaceRules.RuleSource {
      private static final KeyDispatchDataCodec<SurfaceRules.BlockRuleSource> CODEC = KeyDispatchDataCodec.of(
         BlockState.CODEC.xmap(SurfaceRules.BlockRuleSource::new, SurfaceRules.BlockRuleSource::resultState).fieldOf("result_state")
      );

      private BlockRuleSource(final BlockState state) {
         this(state, new SurfaceRules.StateRule(state));
      }

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
         return CODEC;
      }

      public SurfaceRules.SurfaceRule apply(final SurfaceRules.Context context) {
         return this.rule;
      }
   }

   private interface Condition {
      boolean test();
   }

   public interface ConditionSource extends Function<SurfaceRules.Context, SurfaceRules.Condition> {
      Codec<SurfaceRules.ConditionSource> CODEC = BuiltInRegistries.MATERIAL_CONDITION
         .byNameCodec()
         .dispatch(source -> source.codec().codec(), Function.identity());

      static MapCodec<? extends SurfaceRules.ConditionSource> bootstrap(final Registry<MapCodec<? extends SurfaceRules.ConditionSource>> registry) {
         SurfaceRules.register(registry, "biome", SurfaceRules.BiomeConditionSource.CODEC);
         SurfaceRules.register(registry, "noise_threshold", SurfaceRules.NoiseThresholdConditionSource.CODEC);
         SurfaceRules.register(registry, "vertical_gradient", SurfaceRules.VerticalGradientConditionSource.CODEC);
         SurfaceRules.register(registry, "y_above", SurfaceRules.YConditionSource.CODEC);
         SurfaceRules.register(registry, "water", SurfaceRules.WaterConditionSource.CODEC);
         SurfaceRules.register(registry, "temperature", SurfaceRules.Temperature.CODEC);
         SurfaceRules.register(registry, "steep", SurfaceRules.Steep.CODEC);
         SurfaceRules.register(registry, "not", SurfaceRules.NotConditionSource.CODEC);
         SurfaceRules.register(registry, "hole", SurfaceRules.Hole.CODEC);
         SurfaceRules.register(registry, "above_preliminary_surface", SurfaceRules.AbovePreliminarySurface.CODEC);
         return SurfaceRules.register(registry, "stone_depth", SurfaceRules.StoneDepthCheck.CODEC);
      }

      KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec();
   }

   protected static final class Context {
      private static final int HOW_FAR_BELOW_PRELIMINARY_SURFACE_LEVEL_TO_BUILD_SURFACE = 8;
      private static final int SURFACE_CELL_BITS = 4;
      private static final int SURFACE_CELL_SIZE = 16;
      private static final int SURFACE_CELL_MASK = 15;
      private final SurfaceSystem system;
      private final SurfaceRules.Condition temperature = new SurfaceRules.Context.TemperatureHelperCondition(this);
      private final SurfaceRules.Condition steep = new SurfaceRules.Context.SteepMaterialCondition(this);
      private final SurfaceRules.Condition hole = new SurfaceRules.Context.HoleCondition(this);
      private final SurfaceRules.Condition abovePreliminarySurface = new SurfaceRules.Context.AbovePreliminarySurfaceCondition();
      private final RandomState randomState;
      private final ChunkAccess chunk;
      private final NoiseChunk noiseChunk;
      private final Function<BlockPos, Holder<Biome>> biomeGetter;
      private final WorldGenerationContext context;
      private long lastPreliminarySurfaceCellOrigin = Long.MAX_VALUE;
      private final int[] preliminarySurfaceCache = new int[4];
      private long lastUpdateXZ = -9223372036854775807L;
      private int blockX;
      private int blockZ;
      private int surfaceDepth;
      private long lastSurfaceDepth2Update = this.lastUpdateXZ - 1L;
      private double surfaceSecondary;
      private long lastMinSurfaceLevelUpdate = this.lastUpdateXZ - 1L;
      private int minSurfaceLevel;
      private long lastUpdateY = -9223372036854775807L;
      private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      private Supplier<Holder<Biome>> biome;
      private int blockY;
      private int waterHeight;
      private int stoneDepthBelow;
      private int stoneDepthAbove;

      protected Context(
         final SurfaceSystem system,
         final RandomState randomState,
         final ChunkAccess chunk,
         final NoiseChunk noiseChunk,
         final Function<BlockPos, Holder<Biome>> biomeGetter,
         final Registry<Biome> biomes,
         final WorldGenerationContext context
      ) {
         this.system = system;
         this.randomState = randomState;
         this.chunk = chunk;
         this.noiseChunk = noiseChunk;
         this.biomeGetter = biomeGetter;
         this.context = context;
      }

      protected void updateXZ(final int blockX, final int blockZ) {
         this.lastUpdateXZ++;
         this.lastUpdateY++;
         this.blockX = blockX;
         this.blockZ = blockZ;
         this.surfaceDepth = this.system.getSurfaceDepth(blockX, blockZ);
      }

      protected void updateY(final int stoneDepthAbove, final int stoneDepthBelow, final int waterHeight, final int blockX, final int blockY, final int blockZ) {
         this.lastUpdateY++;
         this.biome = Suppliers.memoize(() -> this.biomeGetter.apply(this.pos.set(blockX, blockY, blockZ)));
         this.blockY = blockY;
         this.waterHeight = waterHeight;
         this.stoneDepthBelow = stoneDepthBelow;
         this.stoneDepthAbove = stoneDepthAbove;
      }

      protected double getSurfaceSecondary() {
         if (this.lastSurfaceDepth2Update != this.lastUpdateXZ) {
            this.lastSurfaceDepth2Update = this.lastUpdateXZ;
            this.surfaceSecondary = this.system.getSurfaceSecondary(this.blockX, this.blockZ);
         }

         return this.surfaceSecondary;
      }

      public int getSeaLevel() {
         return this.system.getSeaLevel();
      }

      private static int blockCoordToSurfaceCell(final int blockCoord) {
         return blockCoord >> 4;
      }

      private static int surfaceCellToBlockCoord(final int cellCoord) {
         return cellCoord << 4;
      }

      protected int getMinSurfaceLevel() {
         if (this.lastMinSurfaceLevelUpdate != this.lastUpdateXZ) {
            this.lastMinSurfaceLevelUpdate = this.lastUpdateXZ;
            int cornerCellX = blockCoordToSurfaceCell(this.blockX);
            int cornerCellZ = blockCoordToSurfaceCell(this.blockZ);
            long preliminarySurfaceCellOrigin = ChunkPos.asLong(cornerCellX, cornerCellZ);
            if (this.lastPreliminarySurfaceCellOrigin != preliminarySurfaceCellOrigin) {
               this.lastPreliminarySurfaceCellOrigin = preliminarySurfaceCellOrigin;
               this.preliminarySurfaceCache[0] = this.noiseChunk
                  .preliminarySurfaceLevel(surfaceCellToBlockCoord(cornerCellX), surfaceCellToBlockCoord(cornerCellZ));
               this.preliminarySurfaceCache[1] = this.noiseChunk
                  .preliminarySurfaceLevel(surfaceCellToBlockCoord(cornerCellX + 1), surfaceCellToBlockCoord(cornerCellZ));
               this.preliminarySurfaceCache[2] = this.noiseChunk
                  .preliminarySurfaceLevel(surfaceCellToBlockCoord(cornerCellX), surfaceCellToBlockCoord(cornerCellZ + 1));
               this.preliminarySurfaceCache[3] = this.noiseChunk
                  .preliminarySurfaceLevel(surfaceCellToBlockCoord(cornerCellX + 1), surfaceCellToBlockCoord(cornerCellZ + 1));
            }

            int preliminarySurfaceLevel = Mth.floor(
               Mth.lerp2(
                  (this.blockX & 15) / 16.0F,
                  (this.blockZ & 15) / 16.0F,
                  this.preliminarySurfaceCache[0],
                  this.preliminarySurfaceCache[1],
                  this.preliminarySurfaceCache[2],
                  this.preliminarySurfaceCache[3]
               )
            );
            this.minSurfaceLevel = preliminarySurfaceLevel + this.surfaceDepth - 8;
         }

         return this.minSurfaceLevel;
      }

      private final class AbovePreliminarySurfaceCondition implements SurfaceRules.Condition {
         @Override
         public boolean test() {
            return Context.this.blockY >= Context.this.getMinSurfaceLevel();
         }
      }

      private static final class HoleCondition extends SurfaceRules.LazyXZCondition {
         private HoleCondition(final SurfaceRules.Context context) {
            super(context);
         }

         @Override
         protected boolean compute() {
            return this.context.surfaceDepth <= 0;
         }
      }

      private static class SteepMaterialCondition extends SurfaceRules.LazyXZCondition {
         private SteepMaterialCondition(final SurfaceRules.Context context) {
            super(context);
         }

         @Override
         protected boolean compute() {
            int chunkBlockX = this.context.blockX & 15;
            int chunkBlockZ = this.context.blockZ & 15;
            int zNorth = Math.max(chunkBlockZ - 1, 0);
            int zSouth = Math.min(chunkBlockZ + 1, 15);
            ChunkAccess chunk = this.context.chunk;
            int heightNorth = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, chunkBlockX, zNorth);
            int heightSouth = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, chunkBlockX, zSouth);
            if (heightSouth >= heightNorth + 4) {
               return true;
            }

            int xWest = Math.max(chunkBlockX - 1, 0);
            int xEast = Math.min(chunkBlockX + 1, 15);
            int heightWest = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, xWest, chunkBlockZ);
            int heightEast = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, xEast, chunkBlockZ);
            return heightWest >= heightEast + 4;
         }
      }

      private static class TemperatureHelperCondition extends SurfaceRules.LazyYCondition {
         private TemperatureHelperCondition(final SurfaceRules.Context context) {
            super(context);
         }

         @Override
         protected boolean compute() {
            return this.context
               .biome
               .get()
               .value()
               .coldEnoughToSnow(this.context.pos.set(this.context.blockX, this.context.blockY, this.context.blockZ), this.context.getSeaLevel());
         }
      }
   }

   private enum Hole implements SurfaceRules.ConditionSource {
      INSTANCE;

      private static final KeyDispatchDataCodec<SurfaceRules.Hole> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
         return CODEC;
      }

      public SurfaceRules.Condition apply(final SurfaceRules.Context context) {
         return context.hole;
      }
   }

   private abstract static class LazyCondition implements SurfaceRules.Condition {
      protected final SurfaceRules.Context context;
      private long lastUpdate;
      @Nullable Boolean result;

      protected LazyCondition(final SurfaceRules.Context context) {
         this.context = context;
         this.lastUpdate = this.getContextLastUpdate() - 1L;
      }

      @Override
      public boolean test() {
         long lastContextUpdate = this.getContextLastUpdate();
         if (lastContextUpdate == this.lastUpdate) {
            if (this.result == null) {
               throw new IllegalStateException("Update triggered but the result is null");
            } else {
               return this.result;
            }
         } else {
            this.lastUpdate = lastContextUpdate;
            this.result = this.compute();
            return this.result;
         }
      }

      protected abstract long getContextLastUpdate();

      protected abstract boolean compute();
   }

   private abstract static class LazyXZCondition extends SurfaceRules.LazyCondition {
      protected LazyXZCondition(final SurfaceRules.Context context) {
         super(context);
      }

      @Override
      protected long getContextLastUpdate() {
         return this.context.lastUpdateXZ;
      }
   }

   private abstract static class LazyYCondition extends SurfaceRules.LazyCondition {
      protected LazyYCondition(final SurfaceRules.Context context) {
         super(context);
      }

      @Override
      protected long getContextLastUpdate() {
         return this.context.lastUpdateY;
      }
   }

   private record NoiseThresholdConditionSource(ResourceKey<NormalNoise.NoiseParameters> noise, double minThreshold, double maxThreshold)
      implements SurfaceRules.ConditionSource {
      private static final KeyDispatchDataCodec<SurfaceRules.NoiseThresholdConditionSource> CODEC = KeyDispatchDataCodec.of(
         RecordCodecBuilder.mapCodec(
            i -> i.group(
                  ResourceKey.codec(Registries.NOISE).fieldOf("noise").forGetter(SurfaceRules.NoiseThresholdConditionSource::noise),
                  Codec.DOUBLE.fieldOf("min_threshold").forGetter(SurfaceRules.NoiseThresholdConditionSource::minThreshold),
                  Codec.DOUBLE.fieldOf("max_threshold").forGetter(SurfaceRules.NoiseThresholdConditionSource::maxThreshold)
               )
               .apply(i, SurfaceRules.NoiseThresholdConditionSource::new)
         )
      );

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
         return CODEC;
      }

      public SurfaceRules.Condition apply(final SurfaceRules.Context ruleContext) {
         final NormalNoise noise = ruleContext.randomState.getOrCreateNoise(this.noise);

         class NoiseThresholdCondition extends SurfaceRules.LazyXZCondition {
            private NoiseThresholdCondition() {
               super(ruleContext);
            }

            @Override
            protected boolean compute() {
               double value = noise.getValue(this.context.blockX, 0.0, this.context.blockZ);
               return value >= NoiseThresholdConditionSource.this.minThreshold && value <= NoiseThresholdConditionSource.this.maxThreshold;
            }
         }

         return new NoiseThresholdCondition();
      }
   }

   private record NotCondition(SurfaceRules.Condition target) implements SurfaceRules.Condition {
      @Override
      public boolean test() {
         return !this.target.test();
      }
   }

   private record NotConditionSource(SurfaceRules.ConditionSource target) implements SurfaceRules.ConditionSource {
      private static final KeyDispatchDataCodec<SurfaceRules.NotConditionSource> CODEC = KeyDispatchDataCodec.of(
         SurfaceRules.ConditionSource.CODEC.xmap(SurfaceRules.NotConditionSource::new, SurfaceRules.NotConditionSource::target).fieldOf("invert")
      );

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
         return CODEC;
      }

      public SurfaceRules.Condition apply(final SurfaceRules.Context context) {
         return new SurfaceRules.NotCondition(this.target.apply(context));
      }
   }

   public interface RuleSource extends Function<SurfaceRules.Context, SurfaceRules.SurfaceRule> {
      Codec<SurfaceRules.RuleSource> CODEC = BuiltInRegistries.MATERIAL_RULE.byNameCodec().dispatch(source -> source.codec().codec(), Function.identity());

      static MapCodec<? extends SurfaceRules.RuleSource> bootstrap(final Registry<MapCodec<? extends SurfaceRules.RuleSource>> registry) {
         SurfaceRules.register(registry, "bandlands", SurfaceRules.Bandlands.CODEC);
         SurfaceRules.register(registry, "block", SurfaceRules.BlockRuleSource.CODEC);
         SurfaceRules.register(registry, "sequence", SurfaceRules.SequenceRuleSource.CODEC);
         return SurfaceRules.register(registry, "condition", SurfaceRules.TestRuleSource.CODEC);
      }

      KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec();
   }

   private record SequenceRule(List<SurfaceRules.SurfaceRule> rules) implements SurfaceRules.SurfaceRule {
      @Override
      public @Nullable BlockState tryApply(final int blockX, final int blockY, final int blockZ) {
         for (SurfaceRules.SurfaceRule rule : this.rules) {
            BlockState state = rule.tryApply(blockX, blockY, blockZ);
            if (state != null) {
               return state;
            }
         }

         return null;
      }
   }

   private record SequenceRuleSource(List<SurfaceRules.RuleSource> sequence) implements SurfaceRules.RuleSource {
      private static final KeyDispatchDataCodec<SurfaceRules.SequenceRuleSource> CODEC = KeyDispatchDataCodec.of(
         SurfaceRules.RuleSource.CODEC.listOf().xmap(SurfaceRules.SequenceRuleSource::new, SurfaceRules.SequenceRuleSource::sequence).fieldOf("sequence")
      );

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
         return CODEC;
      }

      public SurfaceRules.SurfaceRule apply(final SurfaceRules.Context context) {
         if (this.sequence.size() == 1) {
            return this.sequence.get(0).apply(context);
         }

         Builder<SurfaceRules.SurfaceRule> builder = ImmutableList.builder();

         for (SurfaceRules.RuleSource rule : this.sequence) {
            builder.add(rule.apply(context));
         }

         return new SurfaceRules.SequenceRule(builder.build());
      }
   }

   private record StateRule(BlockState state) implements SurfaceRules.SurfaceRule {
      @Override
      public BlockState tryApply(final int blockX, final int blockY, final int blockZ) {
         return this.state;
      }
   }

   private enum Steep implements SurfaceRules.ConditionSource {
      INSTANCE;

      private static final KeyDispatchDataCodec<SurfaceRules.Steep> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
         return CODEC;
      }

      public SurfaceRules.Condition apply(final SurfaceRules.Context context) {
         return context.steep;
      }
   }

   private record StoneDepthCheck(int offset, boolean addSurfaceDepth, int secondaryDepthRange, CaveSurface surfaceType)
      implements SurfaceRules.ConditionSource {
      private static final KeyDispatchDataCodec<SurfaceRules.StoneDepthCheck> CODEC = KeyDispatchDataCodec.of(
         RecordCodecBuilder.mapCodec(
            i -> i.group(
                  Codec.INT.fieldOf("offset").forGetter(SurfaceRules.StoneDepthCheck::offset),
                  Codec.BOOL.fieldOf("add_surface_depth").forGetter(SurfaceRules.StoneDepthCheck::addSurfaceDepth),
                  Codec.INT.fieldOf("secondary_depth_range").forGetter(SurfaceRules.StoneDepthCheck::secondaryDepthRange),
                  CaveSurface.CODEC.fieldOf("surface_type").forGetter(SurfaceRules.StoneDepthCheck::surfaceType)
               )
               .apply(i, SurfaceRules.StoneDepthCheck::new)
         )
      );

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
         return CODEC;
      }

      public SurfaceRules.Condition apply(final SurfaceRules.Context ruleContext) {
         final boolean ceiling = this.surfaceType == CaveSurface.CEILING;

         class StoneDepthCondition extends SurfaceRules.LazyYCondition {
            private StoneDepthCondition() {
               super(ruleContext);
            }

            @Override
            protected boolean compute() {
               int stoneDepth = ceiling ? this.context.stoneDepthBelow : this.context.stoneDepthAbove;
               int surfaceDepth = StoneDepthCheck.this.addSurfaceDepth ? this.context.surfaceDepth : 0;
               int secondarySurfaceDepth = StoneDepthCheck.this.secondaryDepthRange == 0
                  ? 0
                  : (int)Mth.map(this.context.getSurfaceSecondary(), -1.0, 1.0, 0.0, StoneDepthCheck.this.secondaryDepthRange);
               return stoneDepth <= 1 + StoneDepthCheck.this.offset + surfaceDepth + secondarySurfaceDepth;
            }
         }

         return new StoneDepthCondition();
      }
   }

   protected interface SurfaceRule {
      @Nullable BlockState tryApply(final int blockX, final int blockY, final int blockZ);
   }

   private enum Temperature implements SurfaceRules.ConditionSource {
      INSTANCE;

      private static final KeyDispatchDataCodec<SurfaceRules.Temperature> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
         return CODEC;
      }

      public SurfaceRules.Condition apply(final SurfaceRules.Context context) {
         return context.temperature;
      }
   }

   private record TestRule(SurfaceRules.Condition condition, SurfaceRules.SurfaceRule followup) implements SurfaceRules.SurfaceRule {
      @Override
      public @Nullable BlockState tryApply(final int blockX, final int blockY, final int blockZ) {
         return !this.condition.test() ? null : this.followup.tryApply(blockX, blockY, blockZ);
      }
   }

   private record TestRuleSource(SurfaceRules.ConditionSource ifTrue, SurfaceRules.RuleSource thenRun) implements SurfaceRules.RuleSource {
      private static final KeyDispatchDataCodec<SurfaceRules.TestRuleSource> CODEC = KeyDispatchDataCodec.of(
         RecordCodecBuilder.mapCodec(
            i -> i.group(
                  SurfaceRules.ConditionSource.CODEC.fieldOf("if_true").forGetter(SurfaceRules.TestRuleSource::ifTrue),
                  SurfaceRules.RuleSource.CODEC.fieldOf("then_run").forGetter(SurfaceRules.TestRuleSource::thenRun)
               )
               .apply(i, SurfaceRules.TestRuleSource::new)
         )
      );

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
         return CODEC;
      }

      public SurfaceRules.SurfaceRule apply(final SurfaceRules.Context context) {
         return new SurfaceRules.TestRule(this.ifTrue.apply(context), this.thenRun.apply(context));
      }
   }

   private record VerticalGradientConditionSource(Identifier randomName, VerticalAnchor trueAtAndBelow, VerticalAnchor falseAtAndAbove)
      implements SurfaceRules.ConditionSource {
      private static final KeyDispatchDataCodec<SurfaceRules.VerticalGradientConditionSource> CODEC = KeyDispatchDataCodec.of(
         RecordCodecBuilder.mapCodec(
            i -> i.group(
                  Identifier.CODEC.fieldOf("random_name").forGetter(SurfaceRules.VerticalGradientConditionSource::randomName),
                  VerticalAnchor.CODEC.fieldOf("true_at_and_below").forGetter(SurfaceRules.VerticalGradientConditionSource::trueAtAndBelow),
                  VerticalAnchor.CODEC.fieldOf("false_at_and_above").forGetter(SurfaceRules.VerticalGradientConditionSource::falseAtAndAbove)
               )
               .apply(i, SurfaceRules.VerticalGradientConditionSource::new)
         )
      );

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
         return CODEC;
      }

      public SurfaceRules.Condition apply(final SurfaceRules.Context ruleContext) {
         final int trueAtAndBelow = this.trueAtAndBelow().resolveY(ruleContext.context);
         final int falseAtAndAbove = this.falseAtAndAbove().resolveY(ruleContext.context);
         final PositionalRandomFactory randomFactory = ruleContext.randomState.getOrCreateRandomFactory(this.randomName());

         class VerticalGradientCondition extends SurfaceRules.LazyYCondition {
            private VerticalGradientCondition() {
               super(ruleContext);
            }

            @Override
            protected boolean compute() {
               int blockY = this.context.blockY;
               if (blockY <= trueAtAndBelow) {
                  return true;
               }

               if (blockY >= falseAtAndAbove) {
                  return false;
               }

               double probability = Mth.map(blockY, trueAtAndBelow, falseAtAndAbove, 1.0, 0.0);
               RandomSource random = randomFactory.at(this.context.blockX, blockY, this.context.blockZ);
               return random.nextFloat() < probability;
            }
         }

         return new VerticalGradientCondition();
      }
   }

   private record WaterConditionSource(int offset, int surfaceDepthMultiplier, boolean addStoneDepth) implements SurfaceRules.ConditionSource {
      private static final KeyDispatchDataCodec<SurfaceRules.WaterConditionSource> CODEC = KeyDispatchDataCodec.of(
         RecordCodecBuilder.mapCodec(
            i -> i.group(
                  Codec.INT.fieldOf("offset").forGetter(SurfaceRules.WaterConditionSource::offset),
                  Codec.intRange(-20, 20).fieldOf("surface_depth_multiplier").forGetter(SurfaceRules.WaterConditionSource::surfaceDepthMultiplier),
                  Codec.BOOL.fieldOf("add_stone_depth").forGetter(SurfaceRules.WaterConditionSource::addStoneDepth)
               )
               .apply(i, SurfaceRules.WaterConditionSource::new)
         )
      );

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
         return CODEC;
      }

      public SurfaceRules.Condition apply(final SurfaceRules.Context ruleContext) {
         class WaterCondition extends SurfaceRules.LazyYCondition {
            private WaterCondition() {
               super(ruleContext);
            }

            @Override
            protected boolean compute() {
               return this.context.waterHeight == Integer.MIN_VALUE
                  || this.context.blockY + (WaterConditionSource.this.addStoneDepth ? this.context.stoneDepthAbove : 0)
                     >= this.context.waterHeight
                        + WaterConditionSource.this.offset
                        + this.context.surfaceDepth * WaterConditionSource.this.surfaceDepthMultiplier;
            }
         }

         return new WaterCondition();
      }
   }

   private record YConditionSource(VerticalAnchor anchor, int surfaceDepthMultiplier, boolean addStoneDepth) implements SurfaceRules.ConditionSource {
      private static final KeyDispatchDataCodec<SurfaceRules.YConditionSource> CODEC = KeyDispatchDataCodec.of(
         RecordCodecBuilder.mapCodec(
            i -> i.group(
                  VerticalAnchor.CODEC.fieldOf("anchor").forGetter(SurfaceRules.YConditionSource::anchor),
                  Codec.intRange(-20, 20).fieldOf("surface_depth_multiplier").forGetter(SurfaceRules.YConditionSource::surfaceDepthMultiplier),
                  Codec.BOOL.fieldOf("add_stone_depth").forGetter(SurfaceRules.YConditionSource::addStoneDepth)
               )
               .apply(i, SurfaceRules.YConditionSource::new)
         )
      );

      @Override
      public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
         return CODEC;
      }

      public SurfaceRules.Condition apply(final SurfaceRules.Context ruleContext) {
         class YCondition extends SurfaceRules.LazyYCondition {
            private YCondition() {
               super(ruleContext);
            }

            @Override
            protected boolean compute() {
               return this.context.blockY + (YConditionSource.this.addStoneDepth ? this.context.stoneDepthAbove : 0)
                  >= YConditionSource.this.anchor.resolveY(this.context.context) + this.context.surfaceDepth * YConditionSource.this.surfaceDepthMultiplier;
            }
         }

         return new YCondition();
      }
   }
}
