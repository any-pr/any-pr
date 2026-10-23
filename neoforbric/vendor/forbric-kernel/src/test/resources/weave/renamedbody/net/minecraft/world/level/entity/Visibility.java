package net.minecraft.world.level.entity;

/** Stand-in for the call vanilla's addEntity body makes, and NeoForge's dispatcher no longer does. */
public enum Visibility {
	TICKING;

	public boolean isTicking() {
		return this == TICKING;
	}
}
