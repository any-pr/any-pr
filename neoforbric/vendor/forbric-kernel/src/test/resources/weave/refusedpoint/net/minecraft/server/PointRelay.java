package net.minecraft.server;

import java.util.Optional;

/** The same mixin's other target, still vanilla-shaped: {@code handle(String, Optional)} calls {@link #note}. */
public class PointRelay {
	public void handle(String id, Optional<String> payload) {
		note(id);
	}

	public static void note(String id) {
		PointTrail.add("relay noted " + id + " | ");
	}

	public void tickServer() {
		PointTrail.add("relay ticked | ");
	}
}
