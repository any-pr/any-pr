package net.minecraft.core.registries;

import fixture.fabricfreezehook.Trace;
import net.minecraft.core.MappedRegistry;

/**
 * Hand-written stand-in, not game code. The root every registry is registered into, and the freeze a Fabric mod's
 * injectors hang on: {@code bootStrap} is {@code createContents} then the private {@code freeze}, which closes the root.
 * Native fabric-registry-sync moves this call to after the Fabric mains; the kernel keeps it in {@code Bootstrap}.
 */
public final class BuiltInRegistries {
	public static final MappedRegistry WRITABLE_REGISTRY = new MappedRegistry("minecraft:root");

	private BuiltInRegistries() {
	}

	public static void bootStrap() {
		createContents();
		freeze();
	}

	public static void createContents() {
		Trace.add("createContents");
	}

	private static void freeze() {
		WRITABLE_REGISTRY.freeze();
		Trace.add("freeze");
	}
}
