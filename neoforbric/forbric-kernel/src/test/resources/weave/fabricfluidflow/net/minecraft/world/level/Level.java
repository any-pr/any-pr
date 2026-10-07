package net.minecraft.world.level;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/** Hand-written stand-in, not game code: a level that records the fluid ticks scheduled and the interaction checks. */
public class Level implements LevelReader, LevelAccessor, ScheduledTickAccess {
	public final List<String> ticks = new ArrayList<>();
	public int interactionChecks;

	public BlockState getBlockState(BlockPos pos) {
		return new BlockState();
	}

	public FluidState getFluidState(BlockPos pos) {
		return new FluidState(new Fluid("water"));
	}

	@Override
	public void scheduleTick(BlockPos pos, Fluid fluid, int delay) {
		ticks.add(pos.name());
	}
}
