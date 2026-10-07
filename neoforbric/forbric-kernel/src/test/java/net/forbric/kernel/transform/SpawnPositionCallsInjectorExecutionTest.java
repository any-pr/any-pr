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
 * {@link SpawnPositionCallsInjector}'s output, run: a spawner and a natural spawn decide exactly as NeoForge's hooks
 * did (the event's SUCCEED and FAIL stand; on DEFAULT the rules, skipped for spawn data with custom rules, then the
 * obstruction check), and the two vanilla calls are now made from the caller itself, where a Fabric mixin redirecting
 * them binds.
 *
 * <p>The hook is the kernel's real {@code KernelSpawnPosition}, compiled from {@code src/runtime/java} against
 * stand-ins; the stand-in NeoForge hooks it replaces post the same event and run the same two calls inside themselves.
 * The stand-in {@code Mob} records which method called each check.
 */
@ExecutesInjector(SpawnPositionCallsInjector.class)
class SpawnPositionCallsInjectorExecutionTest {
	private static final Path HOOK_SOURCE = Path.of("src/runtime/java/net/forbric/kernel/runtime/KernelSpawnPosition.java");
	private static final String SPAWNER = SpawnPositionCallsInjector.TARGET;
	private static final String NATURAL = SpawnPositionCallsInjector.NATURAL;
	private static final String EVENT = "net.neoforged.neoforge.event.entity.living.MobSpawnEvent";

	private static final Map<String, String> STAND_INS = Map.ofEntries(
			Map.entry("net.minecraft.world.level.LevelReader", "package net.minecraft.world.level; public interface LevelReader { }"),
			Map.entry("net.minecraft.world.level.LevelAccessor",
					"package net.minecraft.world.level; public interface LevelAccessor extends LevelReader { }"),
			Map.entry("net.minecraft.world.level.ServerLevelAccessor",
					"package net.minecraft.world.level; public interface ServerLevelAccessor extends LevelAccessor { }"),
			Map.entry("net.minecraft.server.level.ServerLevel",
					"package net.minecraft.server.level; public class ServerLevel implements net.minecraft.world.level.ServerLevelAccessor { }"),
			Map.entry("net.minecraft.core.BlockPos", "package net.minecraft.core; public record BlockPos(int x, int y, int z) { }"),
			Map.entry("net.minecraft.world.entity.EntitySpawnReason",
					"package net.minecraft.world.entity; public enum EntitySpawnReason { NATURAL, SPAWNER }"),
			Map.entry("net.minecraft.world.entity.Mob", """
					package net.minecraft.world.entity;

					import java.util.ArrayList;
					import java.util.List;
					import net.minecraft.world.level.LevelAccessor;
					import net.minecraft.world.level.LevelReader;

					public class Mob {
						public static final List<String> checks = new ArrayList<>();
						public boolean rulesAllow = true;
						public boolean roomFree = true;

						public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
							checks.add("rules from " + caller());
							return rulesAllow;
						}

						public boolean checkSpawnObstruction(LevelReader level) {
							checks.add("obstruction from " + caller());
							return roomFree;
						}

						private static String caller() {
							return StackWalker.getInstance().walk(s -> s.skip(2).findFirst().orElseThrow().getMethodName());
						}
					}
					"""),
			Map.entry("net.minecraft.world.level.SpawnData", """
					package net.minecraft.world.level;

					import java.util.Optional;

					public class SpawnData {
						public boolean customRules;

						public Optional<Object> getCustomSpawnRules() {
							return customRules ? Optional.of("light 0-7") : Optional.empty();
						}
					}
					"""),
			Map.entry(EVENT, """
					package net.neoforged.neoforge.event.entity.living;

					import net.minecraft.world.entity.EntitySpawnReason;
					import net.minecraft.world.entity.Mob;
					import net.minecraft.world.level.BaseSpawner;
					import net.minecraft.world.level.ServerLevelAccessor;

					public class MobSpawnEvent {
						public static class PositionCheck extends MobSpawnEvent {
							public enum Result { SUCCEED, DEFAULT, FAIL }

							public static Result listenerAnswer = Result.DEFAULT;
							private Result result = Result.DEFAULT;

							public PositionCheck(Mob mob, ServerLevelAccessor level, EntitySpawnReason reason, BaseSpawner spawner) {
							}

							public Result getResult() {
								return result;
							}

							public void setResult(Result result) {
								this.result = result;
							}
						}
					}
					"""),
			Map.entry("net.neoforged.neoforge.common.NeoForge", """
					package net.neoforged.neoforge.common;

					import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

					public class NeoForge {
						public static class Bus {
							public <T> T post(T event) {
								if (event instanceof MobSpawnEvent.PositionCheck check) check.setResult(MobSpawnEvent.PositionCheck.listenerAnswer);
								return event;
							}
						}

						public static final Bus EVENT_BUS = new Bus();
					}
					"""),
			Map.entry("net.neoforged.neoforge.event.EventHooks", """
					package net.neoforged.neoforge.event;

					import net.minecraft.world.entity.EntitySpawnReason;
					import net.minecraft.world.entity.Mob;
					import net.minecraft.world.level.BaseSpawner;
					import net.minecraft.world.level.ServerLevelAccessor;
					import net.minecraft.world.level.SpawnData;
					import net.neoforged.neoforge.common.NeoForge;
					import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck;

					public class EventHooks {
						public static boolean checkSpawnPositionSpawner(Mob mob, ServerLevelAccessor level, EntitySpawnReason reason,
								SpawnData data, BaseSpawner spawner) {
							PositionCheck event = NeoForge.EVENT_BUS.post(new PositionCheck(mob, level, reason, spawner));
							if (event.getResult() != PositionCheck.Result.DEFAULT) return event.getResult() == PositionCheck.Result.SUCCEED;
							return (data.getCustomSpawnRules().isPresent() || mob.checkSpawnRules(level, reason)) && mob.checkSpawnObstruction(level);
						}

						public static boolean checkSpawnPosition(Mob mob, ServerLevelAccessor level, EntitySpawnReason reason) {
							PositionCheck event = NeoForge.EVENT_BUS.post(new PositionCheck(mob, level, reason, null));
							if (event.getResult() != PositionCheck.Result.DEFAULT) return event.getResult() == PositionCheck.Result.SUCCEED;
							return mob.checkSpawnRules(level, reason) && mob.checkSpawnObstruction(level);
						}
					}
					"""),
			Map.entry(SPAWNER, """
					package net.minecraft.world.level;

					import java.util.ArrayList;
					import java.util.List;
					import net.minecraft.core.BlockPos;
					import net.minecraft.server.level.ServerLevel;
					import net.minecraft.world.entity.EntitySpawnReason;
					import net.minecraft.world.entity.Mob;
					import net.neoforged.neoforge.event.EventHooks;

					public class BaseSpawner {
						public final List<String> spawned = new ArrayList<>();
						public Mob next = new Mob();
						public SpawnData nextSpawnData = new SpawnData();

						public void serverTick(ServerLevel level, BlockPos pos) {
							Mob mob = next;
							if (!EventHooks.checkSpawnPositionSpawner(mob, level, EntitySpawnReason.SPAWNER, nextSpawnData, this)) return;
							spawned.add("mob at " + pos);
						}
					}
					"""),
			Map.entry(NATURAL, """
					package net.minecraft.world.level;

					import net.minecraft.server.level.ServerLevel;
					import net.minecraft.world.entity.EntitySpawnReason;
					import net.minecraft.world.entity.Mob;
					import net.neoforged.neoforge.event.EventHooks;

					public class NaturalSpawner {
						public static boolean isValidPositionForMob(ServerLevel level, Mob mob) {
							return EventHooks.checkSpawnPosition(mob, level, EntitySpawnReason.NATURAL);
						}
					}
					"""));

	private record Game(Map<String, byte[]> original, ClassLoader repaired) {
	}

	private static Game game(Path work) throws Exception {
		assertTrue(Files.isRegularFile(HOOK_SOURCE), "the game-side hook's source is part of the checkout: " + HOOK_SOURCE.toAbsolutePath());
		Map<String, String> sources = new HashMap<>(STAND_INS);
		sources.put("net/forbric/kernel/runtime/KernelSpawnPosition.java", Files.readString(HOOK_SOURCE));
		Map<String, byte[]> original = InjectorExecution.compile(work, sources);
		// The frames are recomputed from the hierarchy read as resources; here those are the stand-ins.
		SpawnPositionCallsInjector injector = new SpawnPositionCallsInjector(
				path -> original.get(path.substring(0, path.length() - ".class".length())));
		Map<String, byte[]> classes = new HashMap<>(original);
		for (String target : List.of(SPAWNER, NATURAL)) {
			String internal = target.replace('.', '/');
			byte[] out = InjectorExecution.transform(injector, target, original.get(internal), EnvType.SERVER);
			assertNotSame(original.get(internal), out, target);
			classes.put(internal, out);
		}
		ClassLoader loader = InjectorExecution.load(classes);
		for (String target : List.of(SPAWNER, NATURAL)) {
			assertEquals("", InjectorExecution.verify(classes.get(target.replace('.', '/')), loader), target);
		}
		return new Game(original, loader);
	}

	private static void answer(ClassLoader loader, String result) throws Throwable {
		Class<?> check = loader.loadClass(EVENT + "$PositionCheck");
		check.getField("listenerAnswer").set(null, Enum.valueOf(loader.loadClass(EVENT + "$PositionCheck$Result").asSubclass(Enum.class), result));
	}

	/** One spawner tick; returns what it spawned and which checks ran from where. */
	private static List<?> tick(ClassLoader loader, boolean rulesAllow, boolean customRules) throws Throwable {
		Object spawner = InjectorExecution.construct(loader.loadClass(SPAWNER));
		Object mob = spawner.getClass().getField("next").get(spawner);
		mob.getClass().getField("rulesAllow").setBoolean(mob, rulesAllow);
		Object data = spawner.getClass().getField("nextSpawnData").get(spawner);
		data.getClass().getField("customRules").setBoolean(data, customRules);
		InjectorExecution.invoke(spawner, "serverTick", InjectorExecution.construct(loader.loadClass("net.minecraft.server.level.ServerLevel")),
				InjectorExecution.construct(loader.loadClass("net.minecraft.core.BlockPos"), 0, 64, 0));
		List<Object> seen = new java.util.ArrayList<>((List<?>) spawner.getClass().getField("spawned").get(spawner));
		@SuppressWarnings("unchecked")
		List<Object> checks = (List<Object>) InjectorExecution.getStatic(loader.loadClass("net.minecraft.world.entity.Mob"), "checks");
		seen.addAll(checks);
		checks.clear();
		return seen;
	}

	@Test void theSpawnerDecidesAsNeoForgeDidWithTheVanillaCallsInServerTick(@TempDir Path work) throws Throwable {
		Game game = game(work);
		ClassLoader loader = game.repaired();
		answer(loader, "DEFAULT");
		assertEquals(List.of("mob at BlockPos[x=0, y=64, z=0]", "rules from serverTick", "obstruction from serverTick"),
				tick(loader, true, false), "on DEFAULT the vanilla checks run, from serverTick itself");
		assertEquals(List.of("rules from serverTick"), tick(loader, false, false), "rules that refuse deny the spawn");
		assertEquals(List.of("mob at BlockPos[x=0, y=64, z=0]", "obstruction from serverTick"), tick(loader, false, true),
				"spawn data with custom rules skips the mob's own rules, as vanilla does");
		answer(loader, "SUCCEED");
		assertEquals(List.of("mob at BlockPos[x=0, y=64, z=0]"), tick(loader, false, false), "a listener's SUCCEED stands");
		answer(loader, "FAIL");
		assertEquals(List.of(), tick(loader, true, false), "a listener's FAIL stands");

		ClassLoader merged = InjectorExecution.load(game.original());
		answer(merged, "DEFAULT");
		assertEquals(List.of("mob at BlockPos[x=0, y=64, z=0]", "rules from checkSpawnPositionSpawner",
				"obstruction from checkSpawnPositionSpawner"), tick(merged, true, false),
				"premise: as merged the calls are inside NeoForge's hook, where a mixin on the spawner cannot reach them");
	}

	@Test void aNaturalSpawnDecidesAsNeoForgeDidWithTheVanillaCallsInTheCaller(@TempDir Path work) throws Throwable {
		ClassLoader loader = game(work).repaired();
		Class<?> mobType = loader.loadClass("net.minecraft.world.entity.Mob");
		Object level = InjectorExecution.construct(loader.loadClass("net.minecraft.server.level.ServerLevel"));
		Object mob = InjectorExecution.construct(mobType);
		answer(loader, "DEFAULT");
		assertEquals(true, InjectorExecution.invokeStatic(loader.loadClass(NATURAL), "isValidPositionForMob", level, mob));
		assertEquals(List.of("rules from isValidPositionForMob", "obstruction from isValidPositionForMob"),
				InjectorExecution.getStatic(mobType, "checks"));
		mobType.getField("roomFree").setBoolean(mob, false);
		assertEquals(false, InjectorExecution.invokeStatic(loader.loadClass(NATURAL), "isValidPositionForMob", level, mob));
		answer(loader, "SUCCEED");
		assertEquals(true, InjectorExecution.invokeStatic(loader.loadClass(NATURAL), "isValidPositionForMob", level, mob),
				"a listener's SUCCEED stands over a blocked position");
	}
}
