package net.forbric.kernel.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;

/**
 * Stand-in for the kernel's game-side relay, with its real name and descriptor: the anchor adapter reads only the call.
 * The real class runs both families' client hooks and needs their whole graph, which a fresh clone does not compile.
 */
public final class KernelForgeClientInit {
	private KernelForgeClientInit() {
	}

	public static void initClientHooks(Minecraft minecraft, ReloadableResourceManager resources) {
		minecraft.trace.add("client hooks");
	}
}
