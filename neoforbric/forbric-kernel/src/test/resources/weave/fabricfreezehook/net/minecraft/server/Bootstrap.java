package net.minecraft.server;

import fixture.fabricfreezehook.Trace;
import net.minecraft.core.registries.BuiltInRegistries;

/** Hand-written stand-in, not game code: the bootstrap where the kernel keeps the registry freeze. */
public final class Bootstrap {
	private Bootstrap() {
	}

	public static void bootStrap() {
		Trace.add("bootstrap:start");
		BuiltInRegistries.bootStrap();
		Trace.add("bootstrap:end");
	}
}
