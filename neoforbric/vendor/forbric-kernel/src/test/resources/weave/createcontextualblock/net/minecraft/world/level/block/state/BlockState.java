package net.minecraft.world.level.block.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;

/** A stand-in: NeoForge's context-aware resistance query, which answers its block's own value. */
public class BlockState {
	private final Block block;

	public BlockState(Block block) {
		this.block = block;
	}

	public Block getBlock() {
		return block;
	}

	public float getExplosionResistance(BlockGetter level, BlockPos pos, Explosion explosion) {
		return block.getExplosionResistance();
	}
}
