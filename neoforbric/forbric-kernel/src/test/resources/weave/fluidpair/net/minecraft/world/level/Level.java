package net.minecraft.world.level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Hand-written stand-in, not game code: a level that knows where a fluid Create reacts sits, and records in order every
 * registry asked, every flow listener asked and every fluid tick scheduled.
 */
public class Level implements LevelReader, LevelAccessor, ScheduledTickAccess {
	public final List<String> trail = new ArrayList<>();
	private final Set<String> reactive;

	public Level(Set<String> reactive) {
		this.reactive = reactive;
	}

	public boolean reactsAt(BlockPos pos) {
		return reactive.contains(pos.name());
	}

	public BlockState getBlockState(BlockPos pos) {
		return new BlockState();
	}

	public FluidState getFluidState(BlockPos pos) {
		return new FluidState(new Fluid("water"));
	}

	@Override
	public void scheduleTick(BlockPos pos, Fluid fluid, int delay) {
		trail.add("flow " + pos.name());
	}
}
