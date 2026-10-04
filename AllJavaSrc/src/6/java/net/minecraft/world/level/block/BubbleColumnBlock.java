package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BubbleColumnBlock extends Block implements BucketPickup {
   public static final MapCodec<BubbleColumnBlock> CODEC = simpleCodec(BubbleColumnBlock::new);
   public static final BooleanProperty DRAG_DOWN = BlockStateProperties.DRAG;
   private static final int CHECK_PERIOD = 5;

   @Override
   public MapCodec<BubbleColumnBlock> codec() {
      return CODEC;
   }

   public BubbleColumnBlock(final BlockBehaviour.Properties properties) {
      super(properties);
      this.registerDefaultState(this.stateDefinition.any().setValue(DRAG_DOWN, true));
   }

   @Override
   protected void entityInside(
      final BlockState state, final Level level, final BlockPos pos, final Entity entity, final InsideBlockEffectApplier effectApplier, final boolean isPrecise
   ) {
      if (isPrecise) {
         BlockState stateAbove = level.getBlockState(pos.above());
         boolean nothingAbove = stateAbove.getCollisionShape(level, pos).isEmpty() && stateAbove.getFluidState().isEmpty();
         if (nothingAbove) {
            entity.onAboveBubbleColumn(state.getValue(DRAG_DOWN), pos);
         } else {
            entity.onInsideBubbleColumn(state.getValue(DRAG_DOWN));
         }
      }
   }

   @Override
   protected void tick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
      updateColumn(level, pos, state, level.getBlockState(pos.below()));
   }

   @Override
   protected FluidState getFluidState(final BlockState state) {
      return Fluids.WATER.getSource(false);
   }

   public static void updateColumn(final LevelAccessor level, final BlockPos origin, final BlockState belowState) {
      updateColumn(level, origin, level.getBlockState(origin), belowState);
   }

   public static void updateColumn(final LevelAccessor level, final BlockPos origin, final BlockState originState, final BlockState belowState) {
      if (canExistIn(originState)) {
         BlockState columnState = getColumnState(belowState);
         level.setBlock(origin, columnState, 2);
         BlockPos.MutableBlockPos pos = origin.mutable().move(Direction.UP);

         while (canExistIn(level.getBlockState(pos))) {
            if (!level.setBlock(pos, columnState, 2)) {
               return;
            }

            pos.move(Direction.UP);
         }
      }
   }

   private static boolean canExistIn(final BlockState state) {
      return state.is(Blocks.BUBBLE_COLUMN) || state.is(Blocks.WATER) && state.getFluidState().getAmount() >= 8 && state.getFluidState().isSource();
   }

   private static BlockState getColumnState(final BlockState belowState) {
      if (belowState.is(Blocks.BUBBLE_COLUMN)) {
         return belowState;
      } else if (belowState.is(Blocks.SOUL_SAND)) {
         return Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(DRAG_DOWN, false);
      } else {
         return belowState.is(Blocks.MAGMA_BLOCK) ? Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(DRAG_DOWN, true) : Blocks.WATER.defaultBlockState();
      }
   }

   @Override
   public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
      double x = pos.getX();
      double y = pos.getY();
      double z = pos.getZ();
      if (state.getValue(DRAG_DOWN)) {
         level.addAlwaysVisibleParticle(ParticleTypes.CURRENT_DOWN, x + 0.5, y + 0.8, z, 0.0, 0.0, 0.0);
         if (random.nextInt(200) == 0) {
            level.playLocalSound(
               x,
               y,
               z,
               SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,
               SoundSource.BLOCKS,
               0.2F + random.nextFloat() * 0.2F,
               0.9F + random.nextFloat() * 0.15F,
               false
            );
         }
      } else {
         level.addAlwaysVisibleParticle(ParticleTypes.BUBBLE_COLUMN_UP, x + 0.5, y, z + 0.5, 0.0, 0.04, 0.0);
         level.addAlwaysVisibleParticle(ParticleTypes.BUBBLE_COLUMN_UP, x + random.nextFloat(), y + random.nextFloat(), z + random.nextFloat(), 0.0, 0.04, 0.0);
         if (random.nextInt(200) == 0) {
            level.playLocalSound(
               x,
               y,
               z,
               SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
               SoundSource.BLOCKS,
               0.2F + random.nextFloat() * 0.2F,
               0.9F + random.nextFloat() * 0.15F,
               false
            );
         }
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
      ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
      if (!state.canSurvive(level, pos)
         || directionToNeighbour == Direction.DOWN
         || directionToNeighbour == Direction.UP && !neighbourState.is(Blocks.BUBBLE_COLUMN) && canExistIn(neighbourState)) {
         ticks.scheduleTick(pos, this, 5);
      }

      return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
   }

   @Override
   protected boolean canSurvive(final BlockState state, final LevelReader level, final BlockPos pos) {
      BlockState belowState = level.getBlockState(pos.below());
      return belowState.is(Blocks.BUBBLE_COLUMN) || belowState.is(Blocks.MAGMA_BLOCK) || belowState.is(Blocks.SOUL_SAND);
   }

   @Override
   protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
      return Shapes.empty();
   }

   @Override
   protected RenderShape getRenderShape(final BlockState state) {
      return RenderShape.INVISIBLE;
   }

   @Override
   protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(DRAG_DOWN);
   }

   @Override
   public ItemStack pickupBlock(final @Nullable LivingEntity user, final LevelAccessor level, final BlockPos pos, final BlockState state) {
      level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
      return new ItemStack(Items.WATER_BUCKET);
   }

   @Override
   public Optional<SoundEvent> getPickupSound() {
      return Fluids.WATER.getPickupSound();
   }
}
