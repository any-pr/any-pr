package net.minecraft.network.chat;

/** Fixture stand-in: a literal text. */
public record Component(String text) {
	@Override
	public String toString() {
		return text;
	}
}
