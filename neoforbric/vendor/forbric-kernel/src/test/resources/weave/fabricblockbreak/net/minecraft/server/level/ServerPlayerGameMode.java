package net.minecraft.server.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hand-written stand-in, not game code: the merged {@code destroyBlock}. It never calls {@code Block.destroy} itself;
 * it hands the removal to its own {@code removeBlock}, once on the creative path and once on the survival path, and
 * that helper calls {@code Block.destroy} exactly when it removed the block.
 */
public class ServerPlayerGameMode {
	protected ServerLevel level;
	protected final ServerPlayer player;
	public boolean creative;

	public ServerPlayerGameMode(ServerLevel level, ServerPlayer player) {
		this.level = level;
		this.player = player;
	}

	public boolean destroyBlock(BlockPos pos) {
		BlockState state = this.level.getBlockState(pos);
		BlockEntity blockEntity = this.level.getBlockEntity(pos);
		Block block = state.getBlock();
		BlockState adjustedState = block.playerWillDestroy(this.level, pos, state, this.player);
		if (creative) {
			removeBlock(pos, adjustedState, false, new ItemStack());
			return true;
		}
		boolean canHarvest = adjustedState.canHarvestBlock(this.level, pos, this.player);
		boolean removed = removeBlock(pos, adjustedState, canHarvest, new ItemStack());
		return removed;
	}

	private boolean removeBlock(BlockPos pos, BlockState state, boolean canHarvest, ItemStack tool) {
		boolean removed = this.level.removeBlock(pos);
		if (removed) state.getBlock().destroy(this.level, pos, state);
		return removed;
	}
}
