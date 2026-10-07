/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.interop;

import net.forbric.api.Ecosystem;
import net.forbric.api.ModPresence;

public final class CompatPluginOwnership {
    private CompatPluginOwnership() { }
    public static boolean controlifyUsesNeoForge() {
        var mod = ModPresence.metadata("controlify");
        return mod != null && mod.getEcosystem() == Ecosystem.NEOFORGE;
    }
}
