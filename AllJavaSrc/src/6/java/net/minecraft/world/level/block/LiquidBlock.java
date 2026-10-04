package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class LiquidBlock extends Block implements BucketPickup {
   private static final Codec<FlowingFluid> FLOWING_FLUID = BuiltInRegistries.FLUID
      .byNameCodec()
      .comapFlatMap(
         fluid -> fluid instanceof FlowingFluid flowing ? DataResult.success(flowing) : DataResult.error(() -> "Not a flowing fluid: " + fluid), fluid -> fluid
      );
   public static final MapCodec<LiquidBlock> CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(FLOWING_FLUID.fieldOf("fluid").forGetter(b -> b.fluid), propertiesCodec()).apply(i, LiquidBlock::new)
   );
   public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;
   protected final FlowingFluid fluid;
   private final List<FluidState> stateCache;
   public static final VoxelShape SHAPE_STABLE = Block.column(16.0, 0.0, 8.0);
   public static final ImmutableList<Direction> POSSIBLE_FLOW_DIRECTIONS = ImmutableList.of(
      Direction.DOWN, Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST
   );

   @Override
   public MapCodec<LiquidBlock> codec() {
      return CODEC;
   }

   protected LiquidBlock(final FlowingFluid fluid, final BlockBehaviour.Properties properties) {
      super(properties);
      this.fluid = fluid;
      this.stateCache = Lists.newArrayList();
      this.stateCache.add(fluid.getSource(false));

      for (int level = 1; level < 8; level++) {
         this.stateCache.add(fluid.getFlowing(8 - level, false));
      }

      this.stateCache.add(fluid.getFlowing(8, true));
      this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 0));
   }

   @Override
   protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
      if (context.alwaysCollideWithFluid()) {
         return Shapes.block();
      } else {
         return context.isAbove(SHAPE_STABLE, pos, true)
               && state.getValue(LEVEL) == 0
               && context.canStandOnFluid(level.getFluidState(pos.above()), state.getFluidState())
            ? SHAPE_STABLE
            : Shapes.empty();
      }
   }

   @Override
   protected boolean isRandomlyTicking(final BlockState state) {
      return state.getFluidState().isRandomlyTicking();
   }

   @Override
   protected void randomTick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
      state.getFluidState().randomTick(level, pos, random);
   }

   @Override
   protected boolean propagatesSkylightDown(final BlockState state) {
      return false;
   }

   @Override
   protected boolean isPathfindable(final BlockState state, final PathComputationType type) {
      return !this.fluid.is(FluidTags.LAVA);
   }

   @Override
   protected FluidState getFluidState(final BlockState state) {
      int level = state.getValue(LEVEL);
      return this.stateCache.get(Math.min(level, 8));
   }

   @Override
   protected boolean skipRendering(final BlockState state, final BlockState neighborState, final Direction direction) {
      return neighborState.getFluidState().getType().isSame(this.fluid);
   }

   @Override
   protected RenderShape getRenderShape(final BlockState state) {
      return RenderShape.INVISIBLE;
   }

   @Override
   protected List<ItemStack> getDrops(final BlockState state, final LootParams.Builder params) {
      return Collections.emptyList();
   }

   @Override
   protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
      return Shapes.empty();
   }

   @Override
   protected void onPlace(final BlockState state, final Level level, final BlockPos pos, final BlockState oldState, final boolean movedByPiston) {
      if (this.shouldSpreadLiquid(level, pos, state)) {
         level.scheduleTick(pos, state.getFluidState().getType(), this.fluid.getTickDelay(level));
      }
   }

   @Override
   protected BlockState updateShape(
      final BlockState state,
      final LevelReader level,
      final ScheduledTickAccess ticks,
      final BlockPos pos,
      final Direction directionToNeighbour,
      final BlockPos neighbourPos,
      final BlockState neighbourState,
      final RandomSource random
   ) {
      if (state.getFluidState().isSource() || neighbourState.getFluidState().isSource()) {
         ticks.scheduleTick(pos, state.getFluidState().getType(), this.fluid.getTickDelay(level));
      }

      return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
   }

   @Override
   protected void neighborChanged(
      final BlockState state, final Level level, final BlockPos pos, final Block block, final @Nullable Orientation orientation, final boolean movedByPiston
   ) {
      if (this.shouldSpreadLiquid(level, pos, state)) {
         level.scheduleTick(pos, state.getFluidState().getType(), this.fluid.getTickDelay(level));
      }
   }

   private boolean shouldSpreadLiquid(final Level level, final BlockPos pos, final BlockState state) {
      if (this.fluid.is(FluidTags.LAVA)) {
         boolean isOverSoulSoil = level.getBlockState(pos.below()).is(Blocks.SOUL_SOIL);
         UnmodifiableIterator var5 = POSSIBLE_FLOW_DIRECTIONS.iterator();

         while (var5.hasNext()) {
            Direction direction = (Direction)var5.next();
            BlockPos neighbourPos = pos.relative(direction.getOpposite());
            if (level.getFluidState(neighbourPos).is(FluidTags.WATER)) {
               Block convertToBlock = level.getFluidState(pos).isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE;
               level.setBlockAndUpdate(pos, convertToBlock.defaultBlockState());
               this.fizz(level, pos);
               return false;
            }

            if (isOverSoulSoil && level.getBlockState(neighbourPos).is(Blocks.BLUE_ICE)) {
               level.setBlockAndUpdate(pos, Blocks.BASALT.defaultBlockState());
               this.fizz(level, pos);
               return false;
            }
         }
      }

      return true;
   }

   private void fizz(final LevelAccessor level, final BlockPos pos) {
      level.levelEvent(1501, pos, 0);
   }

   @Override
   protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(LEVEL);
   }

   @Override
   public ItemStack pickupBlock(final @Nullable LivingEntity user, final LevelAccessor level, final BlockPos pos, final BlockState state) {
      if (state.getValue(LEVEL) == 0) {
         level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
         return new ItemStack(this.fluid.getBucket());
      } else {
         return ItemStack.EMPTY;
      }
   }

   @Override
   public Optional<SoundEvent> getPickupSound() {
      return this.fluid.getPickupSound();
   }
}
