package net.minecraft.world.level.block;

/** A stand-in: a named block that conducts redstone or not. */
public class Block {
	private final String name;
	private final boolean conductor;

	public Block(String name, boolean conductor) {
		this.name = name;
		this.conductor = conductor;
	}

	public String name() {
		return name;
	}

	public boolean conductor() {
		return conductor;
	}
}
