/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.api.EnvType;

/**
 * {@link WidenedFieldTwinInjector}'s output, run: code compiled against vanilla's field descriptors links and reads
 * the right values on the classes the merge re-typed, where as merged it fails with {@code NoSuchFieldError}.
 * <ul>
 *   <li>NARROW, {@code RangedBowAttackGoal.mob} ({@code Monster} in vanilla, {@code Mob} merged): the vanilla-typed read
 *       sees the monster, and null for a ranged mob that is not one;</li>
 *   <li>FRESH+DRAIN, {@code AttributeSupplier$Builder.builder} ({@code ImmutableMap.Builder} in vanilla, {@code Map}
 *       merged): what fabric-object-builder puts into the vanilla-typed builder reaches {@code build()}, and an
 *       attribute NeoForge's {@code add} sets afterwards overrides it.</li>
 * </ul>
 * The "vanilla-compiled" reader is compiled here against vanilla-shaped stand-ins and then run against the merged ones.
 * The hook is the kernel's real {@code KernelWidenedFields}, compiled from {@code src/runtime/java} against stand-ins,
 * Guava's {@code ImmutableMap} among them (Guava is the game's library, not on the test classpath).
 */
@ExecutesInjector(WidenedFieldTwinInjector.class)
class WidenedFieldTwinInjectorExecutionTest {
	private static final Path HOOK_SOURCE = Path.of("src/runtime/java/net/forbric/kernel/runtime/KernelWidenedFields.java");
	private static final String GOAL = "net/minecraft/world/entity/ai/goal/RangedBowAttackGoal";
	private static final String SUPPLIER = "net/minecraft/world/entity/ai/attributes/AttributeSupplier";

	private static final Map<String, String> COMMON = Map.of(
			"com.google.common.collect.ImmutableMap", """
					package com.google.common.collect;

					import java.util.Collections;
					import java.util.LinkedHashMap;
					import java.util.Map;

					public class ImmutableMap<K, V> {
						public static <K, V> Builder<K, V> builder() {
							return new Builder<>();
						}

						public static class Builder<K, V> {
							private final Map<K, V> entries = new LinkedHashMap<>();

							public Builder<K, V> put(K key, V value) {
								entries.put(key, value);
								return this;
							}

							public Builder<K, V> putAll(Map<? extends K, ? extends V> map) {
								entries.putAll(map);
								return this;
							}

							public Map<K, V> buildKeepingLast() {
								return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
							}
						}
					}
					""",
			"net.minecraft.world.entity.Mob", "package net.minecraft.world.entity; public class Mob { }",
			"net.minecraft.world.entity.monster.Monster",
			"package net.minecraft.world.entity.monster; public class Monster extends net.minecraft.world.entity.Mob { }");

	/** The merged shapes: NeoForge's widened goal field and re-typed attribute builder. */
	private static final Map<String, String> MERGED = Map.of(
			"net.minecraft.world.entity.ai.goal.RangedBowAttackGoal", """
					package net.minecraft.world.entity.ai.goal;

					import net.minecraft.world.entity.Mob;

					public class RangedBowAttackGoal {
						private final Mob mob;

						public RangedBowAttackGoal(Mob mob) {
							this.mob = mob;
						}
					}
					""",
			"net.minecraft.world.entity.ai.attributes.AttributeSupplier", """
					package net.minecraft.world.entity.ai.attributes;

					import java.util.HashMap;
					import java.util.LinkedHashMap;
					import java.util.Map;

					public class AttributeSupplier {
						public final Map<Object, Object> instances;

						AttributeSupplier(Map<Object, Object> instances) {
							this.instances = instances;
						}

						public static class Builder {
							private final Map<Object, Object> builder = new HashMap<>();

							public Builder add(Object attribute, double value) {
								builder.put(attribute, value);
								return this;
							}

							public AttributeSupplier build() {
								return new AttributeSupplier(new LinkedHashMap<>(builder));
							}
						}
					}
					""");

	/** Vanilla's shapes, only for compiling the reader against them. */
	private static final Map<String, String> VANILLA = Map.of(
			"net.minecraft.world.entity.ai.goal.RangedBowAttackGoal", """
					package net.minecraft.world.entity.ai.goal;

					import net.minecraft.world.entity.monster.Monster;

					public class RangedBowAttackGoal {
						public Monster mob;
					}
					""",
			"net.minecraft.world.entity.ai.attributes.AttributeSupplier", """
					package net.minecraft.world.entity.ai.attributes;

					import com.google.common.collect.ImmutableMap;

					public class AttributeSupplier {
						public static class Builder {
							public ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder();
						}
					}
					""",
			"fixture.VanillaReader", """
					package fixture;

					import java.util.Map;
					import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
					import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;

					public class VanillaReader {
						public static Object mobOf(RangedBowAttackGoal goal) {
							return goal.mob;
						}

						/** fabric-object-builder's copy into a fresh builder, through its vanilla-typed accessor. */
						public static AttributeSupplier.Builder copyOf(Map<Object, Object> existing) {
							AttributeSupplier.Builder copy = new AttributeSupplier.Builder();
							copy.builder.putAll(existing);
							return copy;
						}
					}
					""");

	private record Game(ClassLoader merged, ClassLoader twinned) {
	}

	private static Game game(Path work) throws Exception {
		assertTrue(Files.isRegularFile(HOOK_SOURCE), "the game-side hook's source is part of the checkout: " + HOOK_SOURCE.toAbsolutePath());
		Map<String, String> sources = new HashMap<>(COMMON);
		sources.putAll(MERGED);
		sources.put("net/forbric/kernel/runtime/KernelWidenedFields.java", Files.readString(HOOK_SOURCE));
		Map<String, byte[]> merged = new HashMap<>(InjectorExecution.compile(work, sources));
		Map<String, String> vanilla = new HashMap<>(COMMON);
		vanilla.putAll(VANILLA);
		merged.put("fixture/VanillaReader", InjectorExecution.compile(work, vanilla).get("fixture/VanillaReader"));

		Map<String, byte[]> twinned = new HashMap<>(merged);
		WidenedFieldTwinInjector injector = new WidenedFieldTwinInjector();
		for (String target : new String[] {GOAL, SUPPLIER + "$Builder"}) {
			byte[] out = InjectorExecution.transform(injector, target.replace('/', '.'), merged.get(target), EnvType.SERVER);
			assertNotSame(merged.get(target), out, target);
			twinned.put(target, out);
		}
		ClassLoader loader = InjectorExecution.load(twinned);
		for (String target : new String[] {GOAL, SUPPLIER + "$Builder"}) {
			assertEquals("", InjectorExecution.verify(twinned.get(target), loader), target);
		}
		return new Game(InjectorExecution.load(merged), loader);
	}

	@Test void aVanillaTypedReadOfTheGoalsMobSeesTheMonster(@TempDir Path work) throws Throwable {
		Game game = game(work);
		ClassLoader loader = game.twinned();
		Class<?> goal = loader.loadClass(GOAL.replace('/', '.'));
		Class<?> reader = loader.loadClass("fixture.VanillaReader");
		Object skeleton = InjectorExecution.construct(loader.loadClass("net.minecraft.world.entity.monster.Monster"));
		assertSame(skeleton, InjectorExecution.invokeStatic(reader, "mobOf", InjectorExecution.construct(goal, skeleton)));
		Object pillager = InjectorExecution.construct(loader.loadClass("net.minecraft.world.entity.Mob"));
		assertNull(InjectorExecution.invokeStatic(reader, "mobOf", InjectorExecution.construct(goal, pillager)),
				"a ranged mob that is no Monster reads as null, not a ClassCastException");

		ClassLoader merged = game.merged();
		Object goalAsMerged = InjectorExecution.construct(merged.loadClass(GOAL.replace('/', '.')),
				InjectorExecution.construct(merged.loadClass("net.minecraft.world.entity.monster.Monster")));
		assertThrows(NoSuchFieldError.class, () -> InjectorExecution.invokeStatic(merged.loadClass("fixture.VanillaReader"),
				"mobOf", goalAsMerged), "premise: as merged, a vanilla-compiled read of the field does not link");
	}

	@Test void whatFabricCopiesIntoTheVanillaBuilderIsBuiltAndNeoForgesAddOverridesIt(@TempDir Path work) throws Throwable {
		ClassLoader loader = game(work).twinned();
		Map<Object, Object> existing = new LinkedHashMap<>();
		existing.put("generic.max_health", 20.0);
		existing.put("generic.movement_speed", 0.25);
		Object builder = InjectorExecution.invokeStatic(loader.loadClass("fixture.VanillaReader"), "copyOf", existing);
		InjectorExecution.invoke(builder, "add", "generic.max_health", 30.0);
		Object built = InjectorExecution.invoke(builder, "build");
		assertEquals(Map.of("generic.max_health", 30.0, "generic.movement_speed", 0.25), built.getClass().getField("instances").get(built),
				"Fabric's copy reaches build(), and the later add wins, Fabric's copy-then-override order");
	}

	@Test void aSecondPassLeavesTheClassesAlone(@TempDir Path work) throws Exception {
		Map<String, String> sources = new HashMap<>(COMMON);
		sources.putAll(MERGED);
		Map<String, byte[]> merged = InjectorExecution.compile(work, sources);
		for (String target : new String[] {GOAL, SUPPLIER + "$Builder"}) {
			byte[] once = InjectorExecution.transform(new WidenedFieldTwinInjector(), target.replace('/', '.'), merged.get(target), EnvType.SERVER);
			assertSame(once, InjectorExecution.transform(new WidenedFieldTwinInjector(), target.replace('/', '.'), once, EnvType.SERVER), target);
		}
	}
}
