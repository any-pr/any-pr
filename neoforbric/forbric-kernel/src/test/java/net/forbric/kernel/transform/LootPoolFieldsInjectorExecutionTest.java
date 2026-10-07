/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import net.fabricmc.api.EnvType;

/**
 * {@link LootPoolFieldsInjector}'s output, run: a pool a mod builds through NeoForge's constructor can be encoded, and a
 * pool decoded through MinecraftForge's has a name.
 *
 * <p>The merged class has two fields called {@code name} (NeoForge's {@code String}, MinecraftForge's
 * {@code Optional}), which Java source cannot declare, so the stand-in is written with {@code forgeName} and renamed
 * to {@code name} in the class file. Its {@code encode()} reads what the merged codec reads, NeoForge's name and
 * MinecraftForge's condition; {@code getName()} is NeoForge's.
 */
@ExecutesInjector(LootPoolFieldsInjector.class)
class LootPoolFieldsInjectorExecutionTest {
	private static final String POOL_INTERNAL = "net/minecraft/world/level/storage/loot/LootPool";

	private static final Map<String, String> STAND_INS = Map.of(
			"net.minecraft.world.level.storage.loot.providers.number.NumberProvider", """
					package net.minecraft.world.level.storage.loot.providers.number;

					public interface NumberProvider {
					}
					""",
			LootPoolFieldsInjector.TARGET, """
					package net.minecraft.world.level.storage.loot;

					import java.util.List;
					import java.util.Optional;
					import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

					public class LootPool {
						private String name;
						private Optional<String> forgeName;
						private Optional<Object> forge_condition;

						LootPool(List<?> entries, List<?> conditions, List<?> functions, NumberProvider rolls,
								NumberProvider bonusRolls, Optional<String> name) {
							this.name = name.orElse(null);
						}

						LootPool(List<?> entries, List<?> conditions, List<?> functions, NumberProvider rolls,
								NumberProvider bonusRolls, Optional<String> name, Optional<Object> condition) {
							this.forgeName = name;
							this.forge_condition = condition;
						}

						public String getName() {
							return name;
						}

						public Optional<String> forgeName() {
							return forgeName;
						}

						public String encode() {
							return name + " if " + forge_condition.map(String::valueOf).orElse("always");
						}
					}
					""");

	/** javac's classes, with MinecraftForge's {@code forgeName} renamed to the {@code name} it shares with NeoForge's. */
	private static Map<String, byte[]> standIns(Path work) throws Exception {
		Map<String, byte[]> classes = new HashMap<>(InjectorExecution.compile(work, STAND_INS));
		ClassWriter writer = new ClassWriter(0);
		new ClassReader(classes.get(POOL_INTERNAL)).accept(new ClassRemapper(writer,
				new SimpleRemapper(Map.of(POOL_INTERNAL + ".forgeName", "name"))), 0);
		classes.put(POOL_INTERNAL, writer.toByteArray());
		return classes;
	}

	private static Object neoForgePool(ClassLoader loader, Optional<String> name) throws Throwable {
		return InjectorExecution.construct(loader.loadClass(LootPoolFieldsInjector.TARGET), List.of(), List.of(), List.of(), null,
				null, name);
	}

	private static Object forgePool(ClassLoader loader, Optional<String> name) throws Throwable {
		return InjectorExecution.construct(loader.loadClass(LootPoolFieldsInjector.TARGET), List.of(), List.of(), List.of(), null,
				null, name, Optional.of("killed_by_player"));
	}

	@Test void eitherFamilysPoolCarriesTheOtherFamilysFields(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = standIns(work);
		byte[] pool = original.get(POOL_INTERNAL);
		byte[] bridged = InjectorExecution.transform(new LootPoolFieldsInjector(), LootPoolFieldsInjector.TARGET, pool,
				EnvType.SERVER);
		assertNotSame(pool, bridged, "the stand-in must carry both families' fields, or the injector leaves it alone");
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(POOL_INTERNAL, bridged);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(bridged, loader));

		Object built = neoForgePool(loader, Optional.of("tombstone:extra"));
		assertEquals("tombstone:extra if always", InjectorExecution.invoke(built, "encode"),
				"a pool a mod built encodes, with no MinecraftForge condition");
		assertEquals(Optional.of("tombstone:extra"), InjectorExecution.invoke(built, "forgeName"));
		assertEquals(Optional.empty(), InjectorExecution.invoke(neoForgePool(loader, Optional.empty()), "forgeName"));

		Object decoded = forgePool(loader, Optional.of("main"));
		assertEquals("main", InjectorExecution.invoke(decoded, "getName"), "a decoded pool keeps its name");
		assertEquals("main if killed_by_player", InjectorExecution.invoke(decoded, "encode"));
		assertNull(InjectorExecution.invoke(forgePool(loader, Optional.empty()), "getName"));

		ClassLoader merged = InjectorExecution.load(original);
		assertThrows(NullPointerException.class, () -> InjectorExecution.invoke(neoForgePool(merged, Optional.of("x")), "encode"),
				"premise: as merged, encoding a mod's pool dies on the null condition");
		assertNull(InjectorExecution.invoke(forgePool(merged, Optional.of("main")), "getName"),
				"premise: as merged, a decoded pool has no name");
		assertSame(bridged, InjectorExecution.transform(new LootPoolFieldsInjector(), LootPoolFieldsInjector.TARGET, bridged,
				EnvType.SERVER), "constructors that already write the other family's fields are left alone");
	}
}
