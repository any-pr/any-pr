package net.fabricmc.fabric.mixin.registry.sync;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.resources.RegistryDataLoader;

/**
 * Synthetic guest mixin in the shape of fabric-registry-sync's: a wrap marks the server's call into the private
 * loader, and the loader's async task is handed that mark. Both name vanilla's overloads. A thread-local stands in
 * for fabric's ScopedValue, which is a preview API on the JDK this compiles for.
 */
@Mixin(RegistryDataLoader.class)
public class RegistryDataLoaderMixin {
	@Unique
	private static final ThreadLocal<Boolean> IS_SERVER = ThreadLocal.withInitial(() -> false);

	@WrapOperation(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/RegistryDataLoader;load(Lnet/minecraft/resources/RegistryDataLoader$LoaderFactory;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
	private static CompletableFuture<String> wrapIsServerCall(@Coerce Object factory, List<String> lookups, List<String> registries,
			Executor executor, Operation<CompletableFuture<String>> original) {
		IS_SERVER.set(true);
		try {
			return original.call(factory, lookups, registries, executor);
		} finally {
			IS_SERVER.set(false);
		}
	}

	@ModifyArg(method = "load(Lnet/minecraft/resources/RegistryDataLoader$LoaderFactory;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;",
			at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
	private static Supplier<String> supplyAsync(Supplier<String> load) {
		boolean server = IS_SERVER.get();
		return () -> load.get() + " server=" + server;
	}
}
