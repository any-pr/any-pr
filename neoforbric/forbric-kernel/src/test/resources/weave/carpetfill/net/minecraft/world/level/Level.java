package net.minecraft.world.level;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Fixture stand-in for the merged level: setBlock hands the notification to NeoForge's markAndNotifyBlock, which makes
 * the neighbour update under UPDATE_NEIGHBORS (flags & 1) and skips the shape updates under UPDATE_KNOWN_SHAPE
 * (flags & 16). setBlock itself makes neither.
 */
public class Level implements LevelAccessor {
	public final List<String> trace = new ArrayList<>();

	public boolean setBlock(BlockPos pos, BlockState state, int flags, int updateLimit) {
		LevelChunk chunk = new LevelChunk();
		BlockState old = chunk.setBlockState(pos, state);
		markAndNotifyBlock(pos, chunk, old, state, flags, updateLimit);
		return true;
	}

	public void markAndNotifyBlock(BlockPos pos, LevelChunk chunk, BlockState oldState, BlockState newState, int flags, int updateLimit) {
		if ((flags & 1) != 0) {
			updateNeighborsAt(pos, oldState.getBlock());
		}
		if ((flags & 16) == 0 && updateLimit > 0) {
			int neighbourFlags = flags & -34;
			newState.updateNeighbourShapes(this, pos, neighbourFlags, updateLimit - 1);
		}
	}

	public void updateNeighborsAt(BlockPos pos, Block block) {
		trace.add("neighbours@" + pos);
	}

	@Override
	public void record(String event) {
		trace.add(event);
	}
}
