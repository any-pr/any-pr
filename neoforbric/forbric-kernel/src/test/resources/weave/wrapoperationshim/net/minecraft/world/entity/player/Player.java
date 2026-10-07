package net.minecraft.world.entity.player;

/** Fixture stand-in. */
public class Player {
	private final String name;

	public Player(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}
}
