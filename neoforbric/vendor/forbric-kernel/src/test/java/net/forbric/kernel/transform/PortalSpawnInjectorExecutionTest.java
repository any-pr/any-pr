/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.api.EnvType;

/**
 * {@link PortalSpawnInjector}'s output on a fire block whose {@code onPlace} asks NeoForge's portal hook: the hook's
 * complete result — including a replacement shape a listener returns — reaches the block's Optional writeback, and an
 * empty answer still means no portal, as on NeoForge.
 *
 * <p>The hook is the kernel's real {@code KernelPortalSpawn}, compiled from {@code src/runtime/java} against
 * stand-ins for the game and NeoForge's event factory.
 */
@ExecutesInjector(PortalSpawnInjector.class)
class PortalSpawnInjectorExecutionTest {
	private static final Path HOOK_SOURCE = Path.of("src/runtime/java/net/forbric/kernel/runtime/KernelPortalSpawn.java");
	private static final String FIRE = PortalSpawnInjector.TARGET;

	private static final Map<String, String> STAND_INS = Map.ofEntries(
			Map.entry("fixture.Trace", "package fixture; public final class Trace { public static final java.util.List<String> events = new java.util.ArrayList<>(); public static String veto = \"\"; }"),
			Map.entry("net.minecraft.core.BlockPos", "package net.minecraft.core; public record BlockPos(int x, int y, int z) { }"),
			Map.entry("net.minecraft.world.level.LevelAccessor", "package net.minecraft.world.level; public interface LevelAccessor { }"),
			Map.entry("net.minecraft.world.level.Level", "package net.minecraft.world.level; public class Level implements LevelAccessor { }"),
			Map.entry("net.minecraft.world.level.block.state.BlockState", "package net.minecraft.world.level.block.state; public class BlockState { }"),
			Map.entry("net.minecraft.world.level.portal.PortalShape", """
					package net.minecraft.world.level.portal;

					import fixture.Trace;
					import net.minecraft.world.level.LevelAccessor;

					public class PortalShape {
						public void createPortalBlocks(LevelAccessor level) {
							Trace.events.add("portal lit");
						}
					}
					"""),
			Map.entry("net.neoforged.neoforge.event.EventHooks", """
					package net.neoforged.neoforge.event;

					import java.util.Optional;
					import fixture.Trace;
					import net.minecraft.core.BlockPos;
					import net.minecraft.world.level.LevelAccessor;
					import net.minecraft.world.level.portal.PortalShape;

					public class EventHooks {
						public static Optional<PortalShape> onTrySpawnPortal(LevelAccessor level, BlockPos pos, Optional<PortalShape> shape) {
							Trace.events.add("NeoForge asked");
							return Trace.veto.equals("neoforge") ? Optional.empty() : shape;
						}
					}
					"""),
			Map.entry(FIRE, """
					package net.minecraft.world.level.block;

					import java.util.Optional;
					import net.minecraft.core.BlockPos;
					import net.minecraft.world.level.Level;
					import net.minecraft.world.level.block.state.BlockState;
					import net.minecraft.world.level.portal.PortalShape;
					import net.neoforged.neoforge.event.EventHooks;

					public class BaseFireBlock {
						protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
							Optional<PortalShape> shape = EventHooks.onTrySpawnPortal(level, pos, Optional.of(new PortalShape()));
							if (shape.isPresent()) shape.get().createPortalBlocks(level);
						}
					}
					"""));

	@SuppressWarnings("unchecked")
	private static List<String> light(ClassLoader loader, String veto) throws Throwable {
		Class<?> trace = loader.loadClass("fixture.Trace");
		trace.getField("veto").set(null, veto);
		List<String> events = (List<String>) trace.getField("events").get(null);
		events.clear();
		Object state = InjectorExecution.construct(loader.loadClass("net.minecraft.world.level.block.state.BlockState"));
		InjectorExecution.invoke(InjectorExecution.construct(loader.loadClass(FIRE)), "onPlace", state,
				InjectorExecution.construct(loader.loadClass("net.minecraft.world.level.Level")),
				InjectorExecution.construct(loader.loadClass("net.minecraft.core.BlockPos"), 0, 64, 0), state, false);
		return List.copyOf(events);
	}

	@Test void theHooksVerdictReachesTheFireAndAVetoKeepsItUnlit(@TempDir Path work) throws Throwable {
		assertTrue(Files.isRegularFile(HOOK_SOURCE), "the game-side hook's source is part of the checkout: " + HOOK_SOURCE.toAbsolutePath());
		Map<String, String> sources = new HashMap<>(STAND_INS);
		sources.put("net/forbric/kernel/runtime/KernelPortalSpawn.java", Files.readString(HOOK_SOURCE));
		Map<String, byte[]> original = InjectorExecution.compile(work, sources);
		String internal = FIRE.replace('.', '/');
		byte[] routed = InjectorExecution.transform(new PortalSpawnInjector(), FIRE, original.get(internal), EnvType.SERVER);
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(internal, routed);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(routed, loader));

		assertEquals(List.of("NeoForge asked", "portal lit"), light(loader, ""));
		assertEquals(List.of("NeoForge asked"), light(loader, "neoforge"), "a NeoForge veto keeps the portal unlit");

		assertSame(routed, InjectorExecution.transform(new PortalSpawnInjector(), FIRE, routed, EnvType.SERVER),
				"a routed onPlace is left alone");
	}
}
