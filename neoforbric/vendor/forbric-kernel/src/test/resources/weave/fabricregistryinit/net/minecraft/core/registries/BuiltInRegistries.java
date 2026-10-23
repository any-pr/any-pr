package net.minecraft.core.registries;

import fixture.fabricregistryinit.Trace;

/**
 * Hand-written stand-in, not game code. Only the two calls the adapter reasons about: {@code bootStrap} is the
 * registration freeze the kernel keeps in the native bootstrap, {@code createContents} the half of it Fabric's
 * redirect would run there instead.
 */
public final class BuiltInRegistries {
	private static boolean frozen;

	private BuiltInRegistries() {
	}

	public static void bootStrap() {
		Trace.add(frozen ? "freeze-again" : "freeze");
		frozen = true;
	}

	public static void createContents() {
		Trace.add("createContents");
	}
}
