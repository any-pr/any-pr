package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.redstone.Orientation;

/**
 * Hand-written stand-in, not game code: the merged liquid block, as both fluid adapters expect it. Placing and
 * neighbour updates ask a carrier's fluid interaction registry (MinecraftForge's on one path, NeoForge's on the other),
 * once each, and schedule a fluid tick when nothing interacts; a shape update schedules one directly. Vanilla's
 * {@code shouldSpreadLiquid} is still declared, and nothing calls it. Public only so the probe can call the three paths.
 */
public class LiquidBlock extends Block {
	private final Fluid fluid;

	public LiquidBlock(Fluid fluid) {
		this.fluid = fluid;
	}

	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!net.minecraftforge.fluids.FluidInteractionRegistry.canInteract(level, pos)) level.scheduleTick(pos, fluid, 5);
	}

	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean movedByPiston) {
		if (!net.neoforged.neoforge.fluids.FluidInteractionRegistry.canInteract(level, pos)) level.scheduleTick(pos, fluid, 5);
	}

	public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		ticks.scheduleTick(pos, fluid, 5);
		return state;
	}

	private boolean shouldSpreadLiquid(Level level, BlockPos pos, BlockState state) {
		return true;
	}
}
