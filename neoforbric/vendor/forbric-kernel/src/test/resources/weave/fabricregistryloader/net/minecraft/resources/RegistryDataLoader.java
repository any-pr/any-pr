package net.minecraft.resources;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Hand-written stand-in, not game code: the merged loader. Vanilla's four-argument entry is a stub that adds the
 * carrier's pending tags; that body calls the private loader once, with the carrier's leniency flag added, and the
 * private loader runs the load on the executor.
 */
public final class RegistryDataLoader {
	public interface LoaderFactory {
		String name();
	}

	private RegistryDataLoader() {
	}

	public static CompletableFuture<String> load(ResourceManager resources, List<String> lookups, List<String> registries, Executor executor) {
		return load(resources, lookups, registries, executor, List.of());
	}

	public static CompletableFuture<String> load(ResourceManager resources, List<String> lookups, List<String> registries,
			Executor executor, List<String> pendingTags) {
		LoaderFactory factory = () -> "resources";
		return load(factory, lookups, registries, executor, true);
	}

	private static CompletableFuture<String> load(LoaderFactory factory, List<String> lookups, List<String> registries,
			Executor executor, boolean lenient) {
		return CompletableFuture.supplyAsync(() -> factory.name() + " lenient=" + lenient, executor);
	}
}
