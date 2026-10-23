/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.runtime;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.neoforge.resource.VanillaServerListeners;

/** Preserve native listener names; give foreign listeners stable, collision-free graph keys. */
public final class KernelServerReloadNames {
	private KernelServerReloadNames() { }
	public static Identifier nameFor(Class<? extends PreparableReloadListener> type) {
		Identifier nativeName = VanillaServerListeners.getNameForClass(type);
		if (nativeName != null) return nativeName;
		return Identifier.fromNamespaceAndPath("forbric", "foreign_reload/"
				+ HexFormat.of().formatHex(type.getName().getBytes(StandardCharsets.UTF_8)));
	}
}
