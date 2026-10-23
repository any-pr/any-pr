package net.minecraft.world.level.levelgen.structure;

/** Stand-in box; its text is what the probe's trace shows. */
public record BoundingBox(int size) {
	@Override
	public String toString() {
		return "box" + size;
	}
}
