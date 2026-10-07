package net.minecraft.server;

import java.util.Optional;

/** The harness's probe: each target ticks, then handles one call, the server through the carrier's overload. */
public class PointProbe {
	public String handle() {
		PointServer server = new PointServer();
		server.tickServer();
		server.handle("dialog", Optional.of("ok"), new StringBuilder(), 7);
		PointRelay relay = new PointRelay();
		relay.tickServer();
		relay.handle("relay", Optional.empty());
		return PointTrail.read();
	}
}
