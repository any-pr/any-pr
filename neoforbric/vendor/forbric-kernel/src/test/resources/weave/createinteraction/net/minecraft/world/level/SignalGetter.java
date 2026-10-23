package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A stand-in for the merged base's SignalGetter: whether a block passes on the weak power around it is NeoForge's
 * context-aware shouldCheckWeakPower, where vanilla asks isRedstoneConductor.
 */
public interface SignalGetter extends BlockGetter {
	int getDirectSignalTo(BlockPos pos);

	default int getSignal(BlockPos pos, Direction direction) {
		BlockState state = this.getBlockState(pos);
		int signal = state.getSignal(this, pos, direction);
		return state.shouldCheckWeakPower(this, pos, direction) ? Math.max(signal, this.getDirectSignalTo(pos)) : signal;
	}
}
