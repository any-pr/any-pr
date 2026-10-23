package net.minecraft.server.level;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Hand-written stand-in, not game code: blocks and block entities by position. */
public class ServerLevel extends Level {
	private final Map<BlockPos, BlockState> blocks = new HashMap<>();
	private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();

	public void place(BlockPos pos, boolean removable, BlockEntity blockEntity) {
		blocks.put(pos, new BlockState(pos.name(), new Block(), removable));
		if (blockEntity != null) blockEntities.put(pos, blockEntity);
	}

	public BlockState getBlockState(BlockPos pos) {
		return blocks.get(pos);
	}

	public BlockEntity getBlockEntity(BlockPos pos) {
		return blockEntities.get(pos);
	}

	public boolean removeBlock(BlockPos pos) {
		if (!blocks.get(pos).removable()) return false;
		blocks.remove(pos);
		blockEntities.remove(pos);
		return true;
	}
}
