package net.minecraft.world.level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

/** Fixture stand-in: blocks by position, the cells a native fluid interaction handles, and what happened. */
public class Level implements LevelAccessor {
	public final List<String> events = new ArrayList<>();
	public final Set<BlockPos> nativelyHandled = new HashSet<>();
	private final Map<BlockPos, BlockState> blocks = new HashMap<>();

	public BlockState getBlockState(BlockPos pos) {
		return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
	}

	public boolean setBlockAndUpdate(BlockPos pos, BlockState state) {
		blocks.put(pos, state);
		return true;
	}

	public void scheduleTick(BlockPos pos, Fluid fluid, int delay) {
		events.add("tick@" + pos);
	}

	@Override
	public void levelEvent(int type, BlockPos pos, int data) {
		events.add("fizz@" + pos);
	}
}
