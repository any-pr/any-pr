/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import net.fabricmc.api.EnvType;

/**
 * The output of the four injectors that call into the kernel's lifecycle from the game, run in the order the game
 * runs it:
 * <ul>
 *   <li>{@link ClientEntrypointHookInjector}: Fabric's {@code Hooks.startClient(gameDirectory, this)} runs inside
 *       {@code Minecraft.<init>} with the singleton live and {@code options} still null;</li>
 *   <li>{@link NeoClientSetupHookInjector}: NeoForge's client setup runs once {@code options} exists, right before
 *       {@code ClientModLoader.finish()};</li>
 *   <li>{@link ClientPackHookInjector} and {@link DataPackHookInjector}: the kernel is handed the pack repository
 *       first, and the carrier's own body still runs after it (it posts {@code AddPackFindersEvent}, which is how mods
 *       register built-in packs).</li>
 * </ul>
 * The hooks are recording stand-ins under {@code KernelLifecycle}'s and Fabric's {@code Hooks}' names, defined
 * child-first, because the real ones drive the whole mod lifecycle. What is under test is where the edits put the
 * calls and what they pass.
 */
@ExecutesInjector({ClientEntrypointHookInjector.class, NeoClientSetupHookInjector.class, ClientPackHookInjector.class,
		DataPackHookInjector.class})
@ResourceLock("system-properties")
class KernelLifecycleHooksExecutionTest {
	private static final String MINECRAFT = "net.minecraft.client.Minecraft";
	private static final String CLIENT_MOD_LOADER = "net.neoforged.neoforge.client.loading.ClientModLoader";
	private static final String PACK_LOADER = "net.neoforged.neoforge.resource.ResourcePackLoader";

	private static final Map<String, String> STAND_INS = Map.of(
			"fixture.Trace", """
					package fixture;

					import java.util.ArrayList;
					import java.util.List;

					public final class Trace {
						public static final List<String> events = new ArrayList<>();
					}
					""",
			"net.minecraft.client.Options", """
					package net.minecraft.client;

					public class Options {
						public Options(Minecraft minecraft) {
						}
					}
					""",
			MINECRAFT, """
					package net.minecraft.client;

					import java.io.File;
					import fixture.Trace;
					import net.neoforged.neoforge.client.loading.ClientModLoader;

					public class Minecraft {
						private static Minecraft instance;
						public final File gameDirectory;
						public Options options;

						public Minecraft(File gameDirectory) {
							instance = this;
							this.gameDirectory = gameDirectory;
							this.options = new Options(this);
							Trace.events.add("options built");
							ClientModLoader.finish();
						}

						public static Minecraft getInstance() {
							return instance;
						}
					}
					""",
			"net.minecraft.server.packs.PackType", """
					package net.minecraft.server.packs;
					public enum PackType { CLIENT_RESOURCES, SERVER_DATA }
					""",
			"net.minecraft.server.packs.repository.PackRepository", """
					package net.minecraft.server.packs.repository;
					public class PackRepository {
					}
					""",
			PACK_LOADER, """
					package net.neoforged.neoforge.resource;

					import fixture.Trace;
					import net.minecraft.server.packs.PackType;
					import net.minecraft.server.packs.repository.PackRepository;

					public class ResourcePackLoader {
						public static void populatePackRepository(PackRepository repository, PackType type, boolean trusted) {
							Trace.events.add("AddPackFindersEvent " + type);
						}
					}
					""",
			CLIENT_MOD_LOADER, """
					package net.neoforged.neoforge.client.loading;

					import fixture.Trace;
					import net.minecraft.server.packs.PackType;
					import net.minecraft.server.packs.repository.PackRepository;
					import net.neoforged.neoforge.resource.ResourcePackLoader;

					public class ClientModLoader {
						public static void finish() {
							Trace.events.add("ClientModLoader.finish");
						}

						public static void setupModResourcePacks(PackRepository repository) {
							ResourcePackLoader.populatePackRepository(repository, PackType.CLIENT_RESOURCES, false);
						}
					}
					""",
			"net.fabricmc.loader.impl.game.minecraft.Hooks", """
					package net.fabricmc.loader.impl.game.minecraft;

					import java.io.File;
					import fixture.Trace;
					import net.minecraft.client.Minecraft;

					public final class Hooks {
						public static void startClient(File runDir, Object gameInstance) {
							Minecraft game = (Minecraft) gameInstance;
							Trace.events.add("Fabric startClient dir=" + runDir.getName() + " live=" + (Minecraft.getInstance() == game)
									+ " options=" + game.options);
						}
					}
					""",
			"net.forbric.kernel.boot.KernelLifecycle", """
					package net.forbric.kernel.boot;

					import fixture.Trace;
					import net.minecraft.client.Minecraft;

					public final class KernelLifecycle {
						public static void onClientEntrypoints() {
							Trace.events.add("kernel client entrypoints options=" + Minecraft.getInstance().options);
						}

						public static void onNeoClientSetup() {
							Trace.events.add("NeoForge client setup options built=" + (Minecraft.getInstance().options != null));
						}

						public static void onClientResourcePacks(Object repository) {
							Trace.events.add("kernel client packs " + repository.getClass().getSimpleName());
						}

						public static void onServerDataPacks(Object repository, Object type) {
							Trace.events.add("kernel data packs " + type);
						}
					}
					""");

	@AfterEach void reset() {
		System.clearProperty(LifecycleHookInjector.FABRIC_HOOKS_SWITCH);
	}

	private static ClassLoader transformed(Map<String, byte[]> original, Map<String, ClassTransformer> edits) {
		Map<String, byte[]> classes = new HashMap<>(original);
		edits.forEach((target, injector) -> {
			String internal = target.replace('.', '/');
			byte[] out = InjectorExecution.transform(injector, target, classes.get(internal), EnvType.CLIENT);
			assertNotSame(classes.get(internal), out, injector.name() + " did not touch " + target);
			classes.put(internal, out);
		});
		ClassLoader loader = InjectorExecution.load(classes);
		edits.keySet().forEach(target -> assertEquals("", InjectorExecution.verify(classes.get(target.replace('.', '/')), loader), target));
		return loader;
	}

	private static List<?> trace(ClassLoader loader) throws ReflectiveOperationException {
		return (List<?>) InjectorExecution.getStatic(loader.loadClass("fixture.Trace"), "events");
	}

	@Test void fabricStartsWithOptionsNullAndNeoForgeSetsUpOnceTheyExist(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = InjectorExecution.compile(work, STAND_INS);
		// One class, two injectors, in the chain's order: the entrypoint hook, then the setup hook.
		Map<String, byte[]> once = new HashMap<>(original);
		once.put(MINECRAFT.replace('.', '/'), InjectorExecution.transform(new ClientEntrypointHookInjector(), MINECRAFT,
				original.get(MINECRAFT.replace('.', '/')), EnvType.CLIENT));
		ClassLoader loader = transformed(once, Map.of(MINECRAFT, new NeoClientSetupHookInjector()));

		File dir = new File("run-dir");
		InjectorExecution.construct(loader.loadClass(MINECRAFT), dir);
		assertEquals(List.of("Fabric startClient dir=run-dir live=true options=null", "options built",
				"NeoForge client setup options built=true", "ClientModLoader.finish"), trace(loader));

		ClassLoader merged = InjectorExecution.load(original);
		InjectorExecution.construct(merged.loadClass(MINECRAFT), dir);
		assertEquals(List.of("options built", "ClientModLoader.finish"), trace(merged), "premise: as merged, neither runs");
	}

	@Test void withFabricHooksOffTheKernelsBareHookRunsInTheSameWindow(@TempDir Path work) throws Throwable {
		System.setProperty(LifecycleHookInjector.FABRIC_HOOKS_SWITCH, "off");
		ClassLoader loader = transformed(InjectorExecution.compile(work, STAND_INS), Map.of(MINECRAFT, new ClientEntrypointHookInjector()));
		InjectorExecution.construct(loader.loadClass(MINECRAFT), new File("run-dir"));
		assertEquals(List.of("kernel client entrypoints options=null", "options built", "ClientModLoader.finish"), trace(loader));
	}

	@Test void theKernelGetsTheRepositoryFirstAndTheCarriersBodyStillRuns(@TempDir Path work) throws Throwable {
		ClassLoader loader = transformed(InjectorExecution.compile(work, STAND_INS), Map.of(
				CLIENT_MOD_LOADER, new ClientPackHookInjector(), PACK_LOADER, new DataPackHookInjector()));
		Object repository = InjectorExecution.construct(loader.loadClass("net.minecraft.server.packs.repository.PackRepository"));
		InjectorExecution.invokeStatic(loader.loadClass(CLIENT_MOD_LOADER), "setupModResourcePacks", repository);
		Object serverData = Enum.valueOf(loader.loadClass("net.minecraft.server.packs.PackType").asSubclass(Enum.class), "SERVER_DATA");
		InjectorExecution.invokeStatic(loader.loadClass(PACK_LOADER), "populatePackRepository", repository, serverData, true);
		assertEquals(List.of("kernel client packs PackRepository", "kernel data packs CLIENT_RESOURCES",
				"AddPackFindersEvent CLIENT_RESOURCES", "kernel data packs SERVER_DATA", "AddPackFindersEvent SERVER_DATA"),
				trace(loader), "the kernel is handed the repository, then the carrier still posts AddPackFindersEvent");
	}
}
