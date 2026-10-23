package net.minecraft.core;

/** Fixture stand-in. */
public record BlockPos(int x, int y, int z) {
	public BlockPos above() {
		return new BlockPos(x, y + 1, z);
	}

	@Override
	public String toString() {
		return x + "," + y + "," + z;
	}
}
