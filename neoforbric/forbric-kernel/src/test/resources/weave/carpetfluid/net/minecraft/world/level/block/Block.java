package net.minecraft.world.level.block;

import net.minecraft.world.level.block.state.BlockState;

/** Fixture stand-in. */
public class Block {
	private final String id;

	public Block(String id) {
		this.id = id;
	}

	public BlockState defaultBlockState() {
		return new BlockState(this);
	}

	@Override
	public String toString() {
		return id;
	}
}
