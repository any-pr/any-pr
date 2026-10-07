package net.minecraft.client.main;

/** Fixture stand-in. */
public record GameConfig(String name) {
	@Override
	public String toString() {
		return name;
	}
}
