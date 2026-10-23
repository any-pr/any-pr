package net.minecraft.world.level.block;

/** A stand-in: a block and the explosion resistance vanilla reads off it. */
public class Block {
	private final float explosionResistance;

	public Block(float explosionResistance) {
		this.explosionResistance = explosionResistance;
	}

	public float getExplosionResistance() {
		return explosionResistance;
	}
}
