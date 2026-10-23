package net.minecraft.world.level;

/** Fixture stand-in. */
public class LevelReader {
	private final String name;

	public LevelReader(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}
}
