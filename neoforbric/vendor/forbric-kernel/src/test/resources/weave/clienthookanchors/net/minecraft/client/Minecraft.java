package net.minecraft.client;

import java.util.ArrayList;
import java.util.List;

import net.forbric.kernel.runtime.KernelForgeClientInit;
import net.minecraft.client.main.GameConfig;
import net.minecraft.server.packs.resources.ReloadableResourceManager;

/**
 * Fixture stand-in for the merged client's constructor as the kernel's coremod pass leaves it: NeoForge's
 * ClientHooks.initClientHooks call now goes to the relay that serves both families, and no direct call is left.
 */
public class Minecraft {
	public final List<String> trace = new ArrayList<>();
	private final ReloadableResourceManager resourceManager = new ReloadableResourceManager();

	public Minecraft(GameConfig config) {
		trace.add("options(" + config + ")");
		KernelForgeClientInit.initClientHooks(this, resourceManager);
		trace.add("window");
	}
}
