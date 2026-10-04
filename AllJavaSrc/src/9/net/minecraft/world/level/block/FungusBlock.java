package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FungusBlock extends VegetationBlock implements BonemealableBlock {
   public static final MapCodec<FungusBlock> CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(
            ResourceKey.codec(Registries.CONFIGURED_FEATURE).fieldOf("feature").forGetter(b -> b.feature),
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("grows_on").forGetter(b -> b.requiredBlock),
            propertiesCodec()
         )
         .apply(i, FungusBlock::new)
   );
   private static final double BONEMEAL_SUCCESS_PROBABILITY = 0.4;
   private static final VoxelShape SHAPE = Block.column(8.0, 0.0, 9.0);
   private final Block requiredBlock;
   private final ResourceKey<ConfiguredFeature<?, ?>> feature;

   @Override
   public MapCodec<FungusBlock> codec() {
      return CODEC;
   }

   protected FungusBlock(final ResourceKey<ConfiguredFeature<?, ?>> feature, final Block requiredBlock, final BlockBehaviour.Properties properties) {
      super(properties);
      this.feature = feature;
      this.requiredBlock = requiredBlock;
   }

   @Override
   protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
      return SHAPE;
   }

   @Override
   protected boolean mayPlaceOn(final BlockState state, final BlockGetter level, final BlockPos pos) {
      return state.is(BlockTags.NYLIUM) || state.is(Blocks.MYCELIUM) || state.is(Blocks.SOUL_SOIL) || super.mayPlaceOn(state, level, pos);
   }

   private Optional<? extends Holder<ConfiguredFeature<?, ?>>> getFeature(final LevelReader level) {
      return level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(this.feature);
   }

   @Override
   public boolean isValidBonemealTarget(final LevelReader level, final BlockPos pos, final BlockState state) {
      BlockState belowState = level.getBlockState(pos.below());
      return belowState.is(this.requiredBlock);
   }

   @Override
   public boolean isBonemealSuccess(final Level level, final RandomSource random, final BlockPos pos, final BlockState state) {
      return random.nextFloat() < 0.4;
   }

   @Override
   public void performBonemeal(final ServerLevel level, final RandomSource random, final BlockPos pos, final BlockState state) {
      this.getFeature(level).ifPresent(feature -> feature.value().place(level, level.getChunkSource().getGenerator(), random, pos));
   }
}
