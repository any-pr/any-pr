package fixture.fabricregistryloader;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.resources.ResourceManager;

/** Loads the registries the way the server does, on a thread of their own, and reports what the load task saw. */
public class Probe {
	public String run() throws Exception {
		Executor worker = task -> new Thread(task, "registry-loader").start();
		return RegistryDataLoader.load(new ResourceManager(), List.of(), List.of(), worker).get(10, TimeUnit.SECONDS);
	}
}
