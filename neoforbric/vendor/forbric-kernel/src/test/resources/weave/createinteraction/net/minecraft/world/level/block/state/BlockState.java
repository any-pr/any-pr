package net.minecraft.world.level.block.state;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;

/** A stand-in: a block's own signal is none; whether it conducts is its block's say, in both spellings. */
public class BlockState {
	private final Block block;

	public BlockState(Block block) {
		this.block = block;
	}

	public Block getBlock() {
		return block;
	}

	public int getSignal(BlockGetter level, BlockPos pos, Direction direction) {
		return 0;
	}

	/** Vanilla's question. */
	public boolean isRedstoneConductor(BlockGetter level, BlockPos pos) {
		return block.conductor();
	}

	/** NeoForge's question, which the merged getSignal asks instead. */
	public boolean shouldCheckWeakPower(SignalGetter level, BlockPos pos, Direction side) {
		return block.conductor();
	}
}
