package net.minecraft.server;

import fixture.fabricregistryinit.Trace;
import net.minecraft.core.registries.BuiltInRegistries;

/** Hand-written stand-in, not game code: a bootstrap that freezes the registries before it wraps the streams. */
public final class Bootstrap {
	private Bootstrap() {
	}

	public static void bootStrap() {
		Trace.add("bootstrap:start");
		BuiltInRegistries.bootStrap();
		wrapStreams();
		Trace.add("bootstrap:end");
	}

	static void wrapStreams() {
		Trace.add("wrapStreams");
	}
}
