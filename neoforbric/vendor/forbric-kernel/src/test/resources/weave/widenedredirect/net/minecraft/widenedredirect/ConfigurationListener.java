package net.minecraft.widenedredirect;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * A game-side target in its merged shape (hand-written; in a game package so preflight judges it as the merged game):
 * each method makes the carrier's widened call where vanilla made the one-argument call a guest redirects.
 */
public class ConfigurationListener {
	private final RegistryAccess registries = () -> "registries";
	private final ConnectionType connectionType = ConnectionType.NEOFORGE;

	/** The reviewed static decorator, as NeoForge's handleConfigurationFinished calls it. */
	public String finish(String packet) {
		return RegistryFriendlyByteBuf.decorator(registries, connectionType).apply(packet);
	}

	/** The same call, in a method whose own argument the guest's redirect captures after the call's. */
	public String finishCaptured(String packet) {
		return RegistryFriendlyByteBuf.decorator(registries, connectionType).apply(packet);
	}

	/** A static call widened the same way that no row reviews. */
	public String encoded(String packet) {
		return RegistryFriendlyByteBuf.codec(packet, connectionType);
	}

	/** The instance call: a redirect of it would replace the carrier's method and every override of it. */
	public String wrapped(String packet) {
		return new RegistryFriendlyByteBuf().wrap(packet, connectionType);
	}

	/** The harness probe: each shape on its own. */
	public String probe() {
		return "finish=" + finish("p") + " captured=" + finishCaptured("q") + " encoded=" + encoded("r") + " wrapped=" + wrapped("s");
	}
}
