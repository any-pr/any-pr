/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.api.EnvType;

/**
 * {@link ServerReloadListenerNamesInjector}'s output, run: a server reload listener a Fabric mod added by mixin gets a
 * stable name from NeoForge's {@code lookupName}, where as merged the lookup throws and world loading aborts; a vanilla
 * listener keeps its own name.
 *
 * <p>The hook is the kernel's real {@code KernelServerReloadNames}, compiled here from {@code src/runtime/java}
 * against stand-ins for the three game types it names, because the game-side set is only built where the game is.
 */
@ExecutesInjector(ServerReloadListenerNamesInjector.class)
class ServerReloadListenerNamesInjectorExecutionTest {
	private static final Path HOOK_SOURCE = Path.of("src/runtime/java/net/forbric/kernel/runtime/KernelServerReloadNames.java");
	private static final String EVENT = ServerReloadListenerNamesInjector.TARGET;

	private static final Map<String, String> STAND_INS = Map.of(
			"net.minecraft.resources.Identifier", """
					package net.minecraft.resources;

					public record Identifier(String namespace, String path) {
						public static Identifier fromNamespaceAndPath(String namespace, String path) {
							return new Identifier(namespace, path);
						}

						@Override
						public String toString() {
							return namespace + ":" + path;
						}
					}
					""",
			"net.minecraft.server.packs.resources.PreparableReloadListener", """
					package net.minecraft.server.packs.resources;
					public interface PreparableReloadListener {
					}
					""",
			"net.neoforged.neoforge.resource.VanillaServerListeners", """
					package net.neoforged.neoforge.resource;

					import java.util.Map;
					import net.minecraft.resources.Identifier;

					public class VanillaServerListeners {
						/** Vanilla's own listeners only; anything else has no name here. */
						public static Identifier getNameForClass(Class<?> type) {
							return type.getName().equals("fixture.RecipeManager") ? new Identifier("minecraft", "recipes") : null;
						}
					}
					""",
			EVENT, """
					package net.neoforged.neoforge.event;

					import net.minecraft.resources.Identifier;
					import net.minecraft.server.packs.resources.PreparableReloadListener;
					import net.neoforged.neoforge.resource.VanillaServerListeners;

					public class AddServerReloadListenersEvent {
						public static Identifier lookupName(PreparableReloadListener listener) {
							Identifier name = VanillaServerListeners.getNameForClass(listener.getClass());
							if (name == null) throw new IllegalArgumentException("no name for listener " + listener.getClass().getName());
							return name;
						}
					}
					""",
			"fixture.RecipeManager", "package fixture; public class RecipeManager implements net.minecraft.server.packs.resources.PreparableReloadListener { }",
			"fixture.FabricRecipeListener", "package fixture; public class FabricRecipeListener implements net.minecraft.server.packs.resources.PreparableReloadListener { }",
			"fixture.FabricTagListener", "package fixture; public class FabricTagListener implements net.minecraft.server.packs.resources.PreparableReloadListener { }");

	private static String name(ClassLoader loader, String listener) throws Throwable {
		Object instance = InjectorExecution.construct(loader.loadClass(listener));
		return String.valueOf(InjectorExecution.invokeStatic(loader.loadClass(EVENT), "lookupName", instance));
	}

	@Test void aMixinAddedListenerIsNamedAndAVanillaOneKeepsItsName(@TempDir Path work) throws Throwable {
		assertTrue(Files.isRegularFile(HOOK_SOURCE), "the game-side hook's source is part of the checkout: " + HOOK_SOURCE.toAbsolutePath());
		Map<String, String> sources = new HashMap<>(STAND_INS);
		sources.put("net/forbric/kernel/runtime/KernelServerReloadNames.java", Files.readString(HOOK_SOURCE));
		Map<String, byte[]> original = InjectorExecution.compile(work, sources);
		String internal = EVENT.replace('.', '/');
		byte[] named = InjectorExecution.transform(new ServerReloadListenerNamesInjector(), EVENT, original.get(internal), EnvType.SERVER);
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(internal, named);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(named, loader));

		assertEquals("minecraft:recipes", name(loader, "fixture.RecipeManager"), "a vanilla listener keeps its own name");
		String hex = HexFormat.of().formatHex("fixture.FabricRecipeListener".getBytes(StandardCharsets.UTF_8));
		assertEquals("forbric:foreign_reload/" + hex, name(loader, "fixture.FabricRecipeListener"));
		assertNotEquals(name(loader, "fixture.FabricRecipeListener"), name(loader, "fixture.FabricTagListener"),
				"two foreign listeners never share a graph key");

		ClassLoader merged = InjectorExecution.load(original);
		assertThrows(IllegalArgumentException.class, () -> name(merged, "fixture.FabricRecipeListener"),
				"premise: as merged, a mixin-added listener aborts the reload");
		assertSame(named, InjectorExecution.transform(new ServerReloadListenerNamesInjector(), EVENT, named, EnvType.SERVER),
				"nothing left to redirect");
	}
}
