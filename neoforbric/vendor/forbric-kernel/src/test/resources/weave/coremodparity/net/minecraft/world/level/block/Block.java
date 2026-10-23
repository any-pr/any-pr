package net.minecraft.world.level.block;

/** Fixture stand-in. */
public class Block {
	private final String id;

	public Block(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}
}
