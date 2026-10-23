package net.minecraft.core;

/** Fixture stand-in: only what the pick-block call passes around. */
public final class BlockPos {
	private final int x, y, z;

	public BlockPos(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public BlockPos above() {
		return new BlockPos(x, y + 1, z);
	}

	@Override
	public String toString() {
		return "(" + x + "," + y + "," + z + ")";
	}
}
