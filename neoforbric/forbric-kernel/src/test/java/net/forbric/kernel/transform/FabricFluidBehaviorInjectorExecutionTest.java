/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import net.fabricmc.api.EnvType;

/**
 * {@link FabricFluidBehaviorInjector}'s output, run: an entity's tick asking about a tag a Fabric mod gave a fluid
 * behaviour gets that behaviour's fluid type, where as merged NeoForge's {@code getFluidTypeByTag} throws and the server
 * stops. Water and lava keep NeoForge's answer, and a tag nobody knows still throws, as on NeoForge.
 *
 * <p>The stand-in keeps the reviewed shape: a static lookup that answers water or lava and ends in one
 * {@code IllegalArgumentException}. The kernel's {@code KernelFluidTypes} is game-side, so a stand-in under its name
 * answers {@code byTag} for the one tag a behaviour is registered for.
 */
@ExecutesInjector(FabricFluidBehaviorInjector.class)
@ResourceLock("system-properties")
class FabricFluidBehaviorInjectorExecutionTest {
	private static final String TAG = "net.minecraft.tags.TagKey";
	private static final String INTERACTION = FabricFluidBehaviorInjector.INTERACTION;

	private static final Map<String, String> STAND_INS = Map.of(
			TAG, """
					package net.minecraft.tags;

					public record TagKey(String id) {
					}
					""",
			FabricFluidBehaviorInjector.TYPE.replace('/', '.'), """
					package net.neoforged.neoforge.fluids;

					public class FluidType {
						public static final FluidType WATER = new FluidType("water");
						public static final FluidType LAVA = new FluidType("lava");

						public final String name;

						public FluidType(String name) {
							this.name = name;
						}
					}
					""",
			"net.forbric.kernel.runtime.KernelFluidTypes", """
					package net.forbric.kernel.runtime;

					import net.minecraft.tags.TagKey;
					import net.neoforged.neoforge.fluids.FluidType;

					public final class KernelFluidTypes {
						public static final FluidType OIL = new FluidType("oil");

						public static FluidType byTag(TagKey tag) {
							return tag.id().equals("c:oil") ? OIL : null;
						}
					}
					""",
			INTERACTION, """
					package net.minecraft.world.entity;

					import java.util.Set;
					import net.minecraft.tags.TagKey;
					import net.neoforged.neoforge.fluids.FluidType;

					public class EntityFluidInteraction {
						Set<FluidType> touching = Set.of(FluidType.WATER);

						public static FluidType getFluidTypeByTag(TagKey tag) {
							if (tag.id().equals("minecraft:water")) return FluidType.WATER;
							if (tag.id().equals("minecraft:lava")) return FluidType.LAVA;
							throw new IllegalArgumentException("Unknown fluid tag " + tag.id());
						}

						/** What fabric-content-registries asks for every registered tag on every entity tick. */
						public boolean isInFluid(TagKey tag) {
							return touching.contains(getFluidTypeByTag(tag));
						}
					}
					""");

	@AfterEach void reset() {
		System.clearProperty(FabricFluidBehaviorInjector.PROPERTY);
	}

	private static Object tag(ClassLoader loader, String id) throws Throwable {
		return InjectorExecution.construct(loader.loadClass(TAG), id);
	}

	@Test void aBehaviourTagHasItsTypeAndAnUnknownTagStillThrows(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = InjectorExecution.compile(work, STAND_INS);
		String internal = INTERACTION.replace('.', '/');
		byte[] repaired = InjectorExecution.transform(new FabricFluidBehaviorInjector(), INTERACTION, original.get(internal), EnvType.SERVER);
		assertNotSame(original.get(internal), repaired, "the reviewed shape was edited");
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(internal, repaired);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(repaired, loader));

		Class<?> interaction = loader.loadClass(INTERACTION);
		Object oil = InjectorExecution.getStatic(loader.loadClass("net.forbric.kernel.runtime.KernelFluidTypes"), "OIL");
		assertSame(oil, InjectorExecution.invokeStatic(interaction, "getFluidTypeByTag", tag(loader, "c:oil")),
				"a Fabric behaviour's tag answers with the behaviour's type");
		Object entity = InjectorExecution.construct(interaction);
		assertEquals(false, InjectorExecution.invoke(entity, "isInFluid", tag(loader, "c:oil")), "the entity tick goes on");
		assertEquals(true, InjectorExecution.invoke(entity, "isInFluid", tag(loader, "minecraft:water")), "NeoForge's water, as before");
		Class<?> type = loader.loadClass(FabricFluidBehaviorInjector.TYPE.replace('/', '.'));
		assertSame(InjectorExecution.getStatic(type, "LAVA"),
				InjectorExecution.invokeStatic(interaction, "getFluidTypeByTag", tag(loader, "minecraft:lava")));
		Object unknown = tag(loader, "c:nobody");
		assertThrows(IllegalArgumentException.class, () -> InjectorExecution.invokeStatic(interaction, "getFluidTypeByTag", unknown),
				"a tag no behaviour is registered for still throws, as on NeoForge");

		ClassLoader merged = InjectorExecution.load(original);
		Object stock = InjectorExecution.construct(merged.loadClass(INTERACTION));
		Object mergedOil = tag(merged, "c:oil");
		assertThrows(IllegalArgumentException.class, () -> InjectorExecution.invoke(stock, "isInFluid", mergedOil),
				"premise: as merged, the first entity tick after a behaviour is registered throws");
		assertSame(repaired, InjectorExecution.transform(new FabricFluidBehaviorInjector(), INTERACTION, repaired, EnvType.SERVER),
				"a lookup that already asks the kernel is left alone");
	}

	@Test void switchedOffTheLookupIsLeftAsMerged(@TempDir Path work) throws Exception {
		byte[] bytes = InjectorExecution.compile(work, STAND_INS).get(INTERACTION.replace('.', '/'));
		System.setProperty(FabricFluidBehaviorInjector.PROPERTY, "off");
		assertSame(bytes, InjectorExecution.transform(new FabricFluidBehaviorInjector(), INTERACTION, bytes, EnvType.SERVER));
	}
}
