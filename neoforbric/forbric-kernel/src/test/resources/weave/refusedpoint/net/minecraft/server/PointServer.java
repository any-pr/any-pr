package net.minecraft.server;

import java.util.Optional;

/**
 * A merged game class whose vanilla {@code handle(String, Optional)} is gone: the carrier's four-argument overload took its
 * name and does the work itself, with no call to {@link PointRelay#note}. In {@code net.minecraft}, so the adapter judges
 * it as the game's.
 */
public class PointServer {
	public void handle(String id, Optional<String> payload, StringBuilder player, Integer profile) {
		PointTrail.add("[carrier handled " + id + "] ");
	}

	public void tickServer() {
		PointTrail.add("server ticked | ");
	}
}
