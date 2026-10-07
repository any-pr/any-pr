package net.minecraft.core;

/** Stand-in position; its text is what the probe's trace shows. */
public record BlockPos(int x, int y, int z) {
	@Override
	public String toString() {
		return x + "/" + y + "/" + z;
	}
}
