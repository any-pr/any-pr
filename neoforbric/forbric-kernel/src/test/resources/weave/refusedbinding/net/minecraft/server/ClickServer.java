package net.minecraft.server;

import java.util.Optional;

/**
 * A merged game class with two methods of one name, the carrier's declared first -- {@code MinecraftServer.handleCustomClickAction}
 * as the byte merge left it: NeoForge's four-argument overload fires the carrier's event and calls vanilla's two-argument
 * one. In {@code net.minecraft}, so the adapter judges it as the game's, which is where its verdict decides anything.
 */
public class ClickServer {
	public final StringBuilder trail = new StringBuilder();

	public void handleCustomClickAction(String id, Optional<String> payload, StringBuilder player, Integer profile) {
		trail.append("[carrier event] ");
		handleCustomClickAction(id, payload);
	}

	public void handleCustomClickAction(String id, Optional<String> payload) {
		trail.append("vanilla handled ").append(id);
		if (payload.isPresent()) trail.append('=').append(payload.get());
	}

	public void tickServer() {
		trail.append("ticked | ");
	}
}
