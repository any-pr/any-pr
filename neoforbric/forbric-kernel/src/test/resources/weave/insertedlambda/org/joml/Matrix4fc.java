package org.joml;

/** Fixture stand-in for the argument the carrier inserted. */
public record Matrix4fc(String name) {
	@Override
	public String toString() {
		return name;
	}
}
