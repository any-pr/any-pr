package fixture.overloadorder;

import java.util.Optional;

/** The harness's probe: one tick, then a click arriving the way the network handler delivers it, through the carrier's overload. */
public class ClickProbe {
	public String click() {
		ClickServer server = new ClickServer();
		server.tickServer();
		server.handleCustomClickAction("dialog", Optional.of("ok"), new StringBuilder(), 7);
		return server.trail.toString();
	}
}
