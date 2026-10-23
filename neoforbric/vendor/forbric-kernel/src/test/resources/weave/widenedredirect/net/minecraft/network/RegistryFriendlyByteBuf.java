package net.minecraft.network;

import java.util.function.Function;

import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * Stand-in for the buffer's factories in their merged shape (hand-written): vanilla's one-argument static decorator is
 * still declared and the merged game calls NeoForge's, with the connection type appended — the call
 * MixinAtWidenedCall.REDIRECTABLE reviews. codec is a static call widened the same way that no row reviews, and wrap an
 * instance one.
 */
public class RegistryFriendlyByteBuf {
	public static Function<String, String> decorator(RegistryAccess registries) {
		return decorator(registries, ConnectionType.OTHER);
	}

	public static Function<String, String> decorator(RegistryAccess registries, ConnectionType type) {
		return payload -> "typed(" + registries.name() + "," + type + ":" + payload + ")";
	}

	public static String codec(String payload) {
		return codec(payload, ConnectionType.OTHER);
	}

	public static String codec(String payload, ConnectionType type) {
		return "codec(" + payload + "," + type + ")";
	}

	public String wrap(String payload) {
		return wrap(payload, ConnectionType.OTHER);
	}

	public String wrap(String payload, ConnectionType type) {
		return "wrap(" + payload + "," + type + ")";
	}
}
