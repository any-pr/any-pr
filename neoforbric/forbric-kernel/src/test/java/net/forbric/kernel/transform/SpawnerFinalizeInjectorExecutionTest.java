/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
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
 * {@link SpawnerFinalizeInjector}'s output, run against the kernel's real game-side
 * {@code KernelSpawnerFinalize}: the redirected call carries the caller's proven {@code ValueInput} and the mob is
 * finalized exactly once, through NeoForge's own hook.
 */
@ExecutesInjector(SpawnerFinalizeInjector.class)
@ResourceLock("system-properties")
class SpawnerFinalizeInjectorExecutionTest {
	private static final Path HOOK_SOURCE = Path.of("src/runtime/java/net/forbric/kernel/runtime/KernelSpawnerFinalize.java");
	private static final String SPAWNER = SpawnerFinalizeInjector.TARGET;

	private static String empty(String binaryName, String kind) {
		int dot = binaryName.lastIndexOf('.');
		return "package " + binaryName.substring(0, dot) + "; public " + kind + " " + binaryName.substring(dot + 1) + " { }";
	}

	private static final Map<String, String> STAND_INS = new HashMap<>(Map.of(
			"net.minecraft.world.entity.EntitySpawnReason", "package net.minecraft.world.entity; public enum EntitySpawnReason { SPAWNER, NATURAL }",
			"net.minecraft.world.DifficultyInstance", "package net.minecraft.world; public record DifficultyInstance(float value) { }",
			"net.minecraft.world.entity.SpawnGroupData", "package net.minecraft.world.entity; public record SpawnGroupData(String gear) { }",
			"net.minecraft.world.level.storage.ValueInput", "package net.minecraft.world.level.storage; public record ValueInput(String tag) { }",
			"net.minecraft.world.level.Level", "package net.minecraft.world.level; public class Level { }",
			"net.minecraft.world.entity.Entity", "package net.minecraft.world.entity; public class Entity { }",
			"net.minecraft.world.entity.Mob", """
					package net.minecraft.world.entity;

					import java.util.ArrayList;
					import java.util.List;
					import net.minecraft.world.DifficultyInstance;
					import net.minecraft.world.level.ServerLevelAccessor;

					public class Mob extends Entity {
						public final List<String> finalized = new ArrayList<>();
						public boolean spawnCancelled;

						public void setSpawnCancelled(boolean cancelled) {
							spawnCancelled = cancelled;
						}

						public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
								SpawnGroupData data) {
							finalized.add(reason + " " + data);
							return data;
						}
					}
					"""));

	static {
		for (String[] type : new String[][] {{"net.minecraft.world.level.ServerLevelAccessor", "interface"},
				{"net.neoforged.neoforge.common.extensions.IOwnedSpawner", "interface"}, {"net.minecraft.util.ProblemReporter", "class"},
				{"net.minecraft.nbt.CompoundTag", "class"}, {"net.minecraft.core.BlockPos", "class"}}) {
			STAND_INS.put(type[0], empty(type[0], type[1]));
		}
		STAND_INS.put("net.minecraft.core.HolderLookup", "package net.minecraft.core; public interface HolderLookup { interface Provider { } }");
		STAND_INS.put("net.minecraft.server.level.ServerLevel", """
				package net.minecraft.server.level;

				import net.minecraft.core.HolderLookup;
				import net.minecraft.world.level.Level;
				import net.minecraft.world.level.ServerLevelAccessor;

				public class ServerLevel extends Level implements ServerLevelAccessor {
					public HolderLookup.Provider registryAccess() {
						return null;
					}
				}
				""");
		STAND_INS.put("net.minecraft.world.level.storage.TagValueInput", """
				package net.minecraft.world.level.storage;

				import net.minecraft.core.HolderLookup;
				import net.minecraft.nbt.CompoundTag;
				import net.minecraft.util.ProblemReporter;

				public class TagValueInput {
					public static ValueInput lastCreated;

					public static ValueInput create(ProblemReporter reporter, HolderLookup.Provider registries, CompoundTag tag) {
						lastCreated = new ValueInput("spawner entity tag");
						return lastCreated;
					}
				}
				""");
		STAND_INS.put("net.minecraft.world.entity.EntityProcessor", """
				package net.minecraft.world.entity;

				public interface EntityProcessor {
					Entity process(Entity entity);
				}
				""");
		STAND_INS.put("net.minecraft.world.entity.EntityType", """
				package net.minecraft.world.entity;

				import net.minecraft.world.level.Level;
				import net.minecraft.world.level.storage.ValueInput;

				public class EntityType {
					public static Entity loadEntityRecursive(ValueInput input, Level level, EntitySpawnReason reason, EntityProcessor processor) {
						return processor.process(new Mob());
					}
				}
				""");
		STAND_INS.put("net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent", """
				package net.neoforged.neoforge.event.entity.living;

				import net.minecraft.world.DifficultyInstance;
				import net.minecraft.world.entity.EntitySpawnReason;
				import net.minecraft.world.entity.SpawnGroupData;

				public class FinalizeSpawnEvent {
					private DifficultyInstance difficulty;
					private SpawnGroupData data;
					private final EntitySpawnReason reason;
					private boolean canceled;

					public FinalizeSpawnEvent(DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
						this.difficulty = difficulty;
						this.reason = reason;
						this.data = data;
					}

					public boolean isCanceled() { return canceled; }
					public void setCanceled(boolean canceled) { this.canceled = canceled; }
					public boolean isSpawnCancelled() { return false; }
					public DifficultyInstance getDifficulty() { return difficulty; }
					public void setDifficulty(DifficultyInstance difficulty) { this.difficulty = difficulty; }
					public SpawnGroupData getSpawnData() { return data; }
					public void setSpawnData(SpawnGroupData data) { this.data = data; }
					public EntitySpawnReason getSpawnType() { return reason; }
				}
				""");
		STAND_INS.put("net.neoforged.neoforge.event.EventHooks", """
				package net.neoforged.neoforge.event;

				import java.util.ArrayList;
				import java.util.List;
				import java.util.function.Consumer;
				import net.minecraft.world.DifficultyInstance;
				import net.minecraft.world.entity.EntitySpawnReason;
				import net.minecraft.world.entity.Mob;
				import net.minecraft.world.entity.SpawnGroupData;
				import net.minecraft.world.level.ServerLevelAccessor;
				import net.neoforged.neoforge.common.extensions.IOwnedSpawner;
				import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

				public class EventHooks {
					public static final List<Consumer<FinalizeSpawnEvent>> listeners = new ArrayList<>();

					/** NeoForge's: its event, then the mob finalized unless cancelled, when asked to initialize. */
					public static FinalizeSpawnEvent finalizeMobSpawnSpawner(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
							EntitySpawnReason reason, SpawnGroupData data, IOwnedSpawner spawner, boolean initialize) {
						FinalizeSpawnEvent event = new FinalizeSpawnEvent(difficulty, reason, data);
						listeners.forEach(listener -> listener.accept(event));
						if (initialize && !event.isCanceled()) mob.finalizeSpawn(level, event.getDifficulty(), reason, event.getSpawnData());
						return event;
					}
				}
				""");
		STAND_INS.put(SPAWNER, """
				package net.minecraft.world.level;

				import net.minecraft.core.BlockPos;
				import net.minecraft.nbt.CompoundTag;
				import net.minecraft.server.level.ServerLevel;
				import net.minecraft.world.DifficultyInstance;
				import net.minecraft.world.entity.Entity;
				import net.minecraft.world.entity.EntitySpawnReason;
				import net.minecraft.world.entity.EntityType;
				import net.minecraft.world.entity.Mob;
				import net.minecraft.world.level.storage.TagValueInput;
				import net.minecraft.world.level.storage.ValueInput;
				import net.neoforged.neoforge.common.extensions.IOwnedSpawner;
				import net.neoforged.neoforge.event.EventHooks;

				public class BaseSpawner implements IOwnedSpawner {
					public Mob spawned;

					/** The carrier body: the entity loaded from the spawner's input, finalized through NeoForge's hook. */
					public void serverTick(ServerLevel level, BlockPos pos) {
						ValueInput input = TagValueInput.create(null, level.registryAccess(), new CompoundTag());
						Entity entity = EntityType.loadEntityRecursive(input, level, EntitySpawnReason.SPAWNER, loaded -> loaded);
						if (entity instanceof Mob mob) {
							EventHooks.finalizeMobSpawnSpawner(mob, level, new DifficultyInstance(1.5F), EntitySpawnReason.SPAWNER, null, this, true);
							spawned = mob;
						}
					}
				}
				""");
		STAND_INS.put("fixture.Listeners", """
				package fixture;

				import java.util.ArrayList;
				import java.util.List;
				import net.neoforged.neoforge.event.EventHooks;

				/** A NeoForge mod watching finalization. */
				public class Listeners {
					public static final List<Object> heard = new ArrayList<>();

					public static void register() {
						EventHooks.listeners.add(event -> heard.add("neoforge"));
					}
				}
				""");
	}

	@AfterEach void reset() {
		System.clearProperty(SpawnerFinalizeInjector.PROPERTY);
	}

	private static Map<String, byte[]> compile(Path work) throws Exception {
		assertTrue(Files.isRegularFile(HOOK_SOURCE), "the game-side hook's source is part of the checkout: " + HOOK_SOURCE.toAbsolutePath());
		Map<String, String> sources = new HashMap<>(STAND_INS);
		sources.put("net/forbric/kernel/runtime/KernelSpawnerFinalize.java", Files.readString(HOOK_SOURCE));
		return InjectorExecution.compile(work, sources);
	}

	/** One spawn: who heard the finalization, then how the mob was finalized. */
	private static List<Object> spawn(ClassLoader loader) throws Throwable {
		InjectorExecution.invokeStatic(loader.loadClass("fixture.Listeners"), "register");
		Object spawner = InjectorExecution.construct(loader.loadClass(SPAWNER));
		InjectorExecution.invoke(spawner, "serverTick", InjectorExecution.construct(loader.loadClass("net.minecraft.server.level.ServerLevel")),
				InjectorExecution.construct(loader.loadClass("net.minecraft.core.BlockPos")));
		Object mob = spawner.getClass().getField("spawned").get(spawner);
		return List.of(List.copyOf((List<?>) InjectorExecution.getStatic(loader.loadClass("fixture.Listeners"), "heard")),
				mob.getClass().getField("finalized").get(mob));
	}

	@Test void theKernelHookFinalizesTheMobOnceThroughNeoForgesEvent(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = compile(work);
		String internal = SPAWNER.replace('.', '/');
		byte[] repaired = InjectorExecution.transform(new SpawnerFinalizeInjector(), SPAWNER, original.get(internal), EnvType.SERVER);
		assertNotSame(original.get(internal), repaired, "the input was proved and handed over");
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(internal, repaired);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(repaired, loader));

		List<Object> spawned = spawn(loader);
		assertEquals(List.of("neoforge"), spawned.get(0), "NeoForge's event heard once");
		assertEquals(List.of("SPAWNER null"), spawned.get(1), "the mob finalized exactly once, through NeoForge's own hook");

		assertSame(repaired, InjectorExecution.transform(new SpawnerFinalizeInjector(), SPAWNER, repaired, EnvType.SERVER),
				"a caller already on the kernel's hook is left alone");
	}

	@Test void switchedOffTheSpawnerIsLeftAsTheCarrierShippedIt(@TempDir Path work) throws Exception {
		byte[] bytes = compile(work).get(SPAWNER.replace('.', '/'));
		System.setProperty(SpawnerFinalizeInjector.PROPERTY, "off");
		assertSame(bytes, InjectorExecution.transform(new SpawnerFinalizeInjector(), SPAWNER, bytes, EnvType.SERVER));
	}
}
