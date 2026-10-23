package net.minecraft.world.level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A stand-in: which positions hold a fluid the mod reacts, and what happened, in order. */
public class Level {
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

	public void scheduleTick(BlockPos pos) {
		trail.add("flow " + pos.name());
	}
}
