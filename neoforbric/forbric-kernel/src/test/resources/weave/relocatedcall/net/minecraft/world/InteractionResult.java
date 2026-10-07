package net.minecraft.world;

/** Fixture stand-in: only its descriptor matters to MixinRelocatedCall, and its name tells the probe who answered. */
public final class InteractionResult {
	private final String name;

	public InteractionResult(String name) {
		this.name = name;
	}

	public String name() {
		return name;
	}
}
