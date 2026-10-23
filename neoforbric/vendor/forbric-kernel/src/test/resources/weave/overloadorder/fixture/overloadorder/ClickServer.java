package fixture.overloadorder;

import java.util.Optional;

/**
 * Shaped like the merged {@code MinecraftServer.handleCustomClickAction}: NeoForge's four-argument overload -- fire the
 * carrier's event, then call vanilla's -- declared BEFORE vanilla's two-argument one, which is where the byte merge left
 * them. javac keeps declaration order, so the class file does too.
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
