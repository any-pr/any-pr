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
 * {@link BiomeLateWriteInjector}'s output, run: once NeoForge's biome pass has run, a climate or effects object a
 * Fabric mod puts into the biome afterwards is what the biome answers, and until then (or while nothing replaced it)
 * the answer is still NeoForge's modified view.
 *
 * <p>The stand-in {@code Biome} keeps NeoForge's getter shape: {@code getModifiedClimateSettings()} and
 * {@code getModifiedSpecialEffects()} answer from the modifiable info, never from the raw fields.
 */
@ExecutesInjector(BiomeLateWriteInjector.class)
@ResourceLock("system-properties")
class BiomeLateWriteInjectorExecutionTest {
	private static final String CLIMATE = "net.minecraft.world.level.biome.Biome$ClimateSettings";
	private static final String EFFECTS = "net.minecraft.world.level.biome.BiomeSpecialEffects";

	private static final Map<String, String> STAND_INS = Map.of(
			EFFECTS, """
					package net.minecraft.world.level.biome;

					public record BiomeSpecialEffects(int fogColor) {
					}
					""",
			BiomeLateWriteInjector.TARGET, """
					package net.minecraft.world.level.biome;

					public class Biome {
						public record ClimateSettings(float temperature) {
						}

						/** NeoForge's modifiable info: the view, fixed when the modifier pass runs. */
						public record ModifiedView(ClimateSettings climate, BiomeSpecialEffects effects) {
						}

						public ClimateSettings climateSettings;
						public BiomeSpecialEffects specialEffects;
						public ModifiedView view;

						public Biome(ClimateSettings climate, BiomeSpecialEffects effects) {
							this.climateSettings = climate;
							this.specialEffects = effects;
							this.view = new ModifiedView(climate, effects);
						}

						public ClimateSettings getModifiedClimateSettings() {
							return view.climate();
						}

						public BiomeSpecialEffects getModifiedSpecialEffects() {
							return view.effects();
						}
					}
					""");

	@AfterEach void reset() {
		System.clearProperty(NativeCoremodParity.BIOME);
	}

	private static Object climate(ClassLoader loader, float temperature) throws Throwable {
		return InjectorExecution.construct(loader.loadClass(CLIMATE), temperature);
	}

	private static Object effects(ClassLoader loader, int fog) throws Throwable {
		return InjectorExecution.construct(loader.loadClass(EFFECTS), fog);
	}

	private static void set(Object biome, String field, Object value) throws ReflectiveOperationException {
		biome.getClass().getField(field).set(biome, value);
	}

	@Test void aClimateOrEffectsReplacedAfterThePassIsWhatTheBiomeAnswers(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = InjectorExecution.compile(work, STAND_INS);
		String internal = BiomeLateWriteInjector.OWNER;
		byte[] guarded = InjectorExecution.transform(new BiomeLateWriteInjector(), BiomeLateWriteInjector.TARGET,
				original.get(internal), EnvType.SERVER);
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(internal, guarded);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(guarded, loader));

		Object plains = climate(loader, 0.8f), fog = effects(loader, 0xC0D8FF);
		Object biome = InjectorExecution.construct(loader.loadClass(BiomeLateWriteInjector.TARGET), plains, fog);
		// The pass's own result: NeoForge's modifiers made it colder and foggier than the raw fields.
		Object modifiedClimate = climate(loader, 0.2f), modifiedEffects = effects(loader, 0x101010);
		set(biome, "view", InjectorExecution.construct(loader.loadClass(BiomeLateWriteInjector.TARGET + "$ModifiedView"),
				modifiedClimate, modifiedEffects));
		Object replaced = climate(loader, 2.0f);
		set(biome, "climateSettings", replaced);
		assertSame(modifiedClimate, InjectorExecution.invoke(biome, "getModifiedClimateSettings"),
				"before the pass has been marked, NeoForge's view answers");

		InjectorExecution.invoke(biome, BiomeLateWriteInjector.MARK, replaced, fog);
		assertSame(modifiedClimate, InjectorExecution.invoke(biome, "getModifiedClimateSettings"),
				"unchanged since the pass: the view");
		assertSame(modifiedEffects, InjectorExecution.invoke(biome, "getModifiedSpecialEffects"));

		Object lithostitched = climate(loader, 1.5f), desertFog = effects(loader, 0xFFE0A0);
		set(biome, "climateSettings", lithostitched);
		set(biome, "specialEffects", desertFog);
		assertSame(lithostitched, InjectorExecution.invoke(biome, "getModifiedClimateSettings"),
				"a climate replaced after the pass wins, as on Fabric");
		assertSame(desertFog, InjectorExecution.invoke(biome, "getModifiedSpecialEffects"));

		ClassLoader merged = InjectorExecution.load(original);
		Object stock = InjectorExecution.construct(merged.loadClass(BiomeLateWriteInjector.TARGET), climate(merged, 0.8f),
				effects(merged, 1));
		Object late = climate(merged, 1.5f);
		set(stock, "climateSettings", late);
		assertNotSame(late, InjectorExecution.invoke(stock, "getModifiedClimateSettings"),
				"premise: without the edit the view ignores a late write");
		assertSame(guarded, InjectorExecution.transform(new BiomeLateWriteInjector(), BiomeLateWriteInjector.TARGET, guarded,
				EnvType.SERVER), "a biome that already has the mark is left alone");
	}

	@Test void switchedOffWithTheViewTheBiomeIsLeftAlone(@TempDir Path work) throws Exception {
		byte[] bytes = InjectorExecution.compile(work, STAND_INS).get(BiomeLateWriteInjector.OWNER);
		System.setProperty(NativeCoremodParity.BIOME, "off");
		assertSame(bytes, InjectorExecution.transform(new BiomeLateWriteInjector(), BiomeLateWriteInjector.TARGET, bytes,
				EnvType.SERVER));
	}
}
