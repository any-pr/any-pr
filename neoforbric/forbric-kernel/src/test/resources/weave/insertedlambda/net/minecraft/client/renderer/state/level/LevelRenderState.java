package net.minecraft.client.renderer.state.level;

/** Fixture stand-in. */
public record LevelRenderState(String name) {
	@Override
	public String toString() {
		return name;
	}
}
