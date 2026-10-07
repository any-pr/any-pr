/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import net.fabricmc.api.EnvType;

/**
 * {@link BiomeInfoRebaseInjector}'s output, run: NeoForge's biome modifier pass starts from the climate a Fabric mod put
 * into the biome before it, and NeoForge's modifiers still apply on top; where as merged the pass starts from the
 * constructor's climate and the Fabric change is gone from the modified view.
 *
 * <p>The hook is the kernel's real {@code KernelBiomeView}, compiled from {@code src/runtime/java} against stand-ins
 * for {@code Holder}, {@code Biome}, {@code BiomeSpecialEffects} and {@code ModifiableBiomeInfo}, whose
 * {@code applyBiomeModifiers} reads {@code getOriginalBiomeInfo()} once and folds the modifiers over it.
 */
@ExecutesInjector(BiomeInfoRebaseInjector.class)
@ResourceLock("system-properties")
class BiomeInfoRebaseInjectorExecutionTest {
	private static final Path HOOK_SOURCE = Path.of("src/runtime/java/net/forbric/kernel/runtime/KernelBiomeView.java");
	private static final String INFO = BiomeInfoRebaseInjector.TARGET;
	private static final String BIOME = "net.minecraft.world.level.biome.Biome";

	private static final Map<String, String> STAND_INS = Map.of(
			"net.minecraft.core.Holder", """
					package net.minecraft.core;

					public interface Holder<T> {
						T value();

						String getRegisteredName();

						record Direct<T>(T value, String getRegisteredName) implements Holder<T> {
						}
					}
					""",
			"net.minecraft.core.RegistryAccess", "package net.minecraft.core; public interface RegistryAccess { }",
			"net.minecraft.world.level.biome.BiomeSpecialEffects",
			"package net.minecraft.world.level.biome; public record BiomeSpecialEffects(int fogColor) { }",
			BIOME, """
					package net.minecraft.world.level.biome;

					public class Biome {
						public record ClimateSettings(float temperature, float downfall) {
						}

						private ClimateSettings climateSettings;
						private BiomeSpecialEffects specialEffects;

						public Biome(ClimateSettings climate, BiomeSpecialEffects effects) {
							this.climateSettings = climate;
							this.specialEffects = effects;
						}

						/** What fabric-biome-api's weather modification does before NeoForge's pass. */
						public void replaceClimate(ClimateSettings climate) {
							this.climateSettings = climate;
						}
					}
					""",
			INFO, """
					package net.neoforged.neoforge.common.world;

					import java.util.List;
					import java.util.function.UnaryOperator;
					import net.minecraft.core.Holder;
					import net.minecraft.core.RegistryAccess;
					import net.minecraft.world.level.biome.Biome;
					import net.minecraft.world.level.biome.BiomeSpecialEffects;

					public class ModifiableBiomeInfo {
						public record BiomeInfo(Biome.ClimateSettings climateSettings, BiomeSpecialEffects effects,
								Object generationSettings, Object mobSpawnSettings) {
						}

						private final BiomeInfo originalBiomeInfo;
						public BiomeInfo modifiedBiomeInfo;

						public ModifiableBiomeInfo(BiomeInfo original) {
							this.originalBiomeInfo = original;
						}

						public BiomeInfo getOriginalBiomeInfo() {
							return originalBiomeInfo;
						}

						public boolean applyBiomeModifiers(Holder<Biome> biome, List<UnaryOperator<BiomeInfo>> modifiers, RegistryAccess access) {
							BiomeInfo info = getOriginalBiomeInfo();
							for (UnaryOperator<BiomeInfo> modifier : modifiers) info = modifier.apply(info);
							this.modifiedBiomeInfo = info;
							return true;
						}
					}
					""");

	@AfterEach void reset() {
		System.clearProperty(BiomeInfoRebaseInjector.DIAGNOSTIC);
	}

	private static Map<String, byte[]> compile(Path work) throws Exception {
		assertTrue(Files.isRegularFile(HOOK_SOURCE), "the game-side hook's source is part of the checkout: " + HOOK_SOURCE.toAbsolutePath());
		Map<String, String> sources = new HashMap<>(STAND_INS);
		sources.put("net/forbric/kernel/runtime/KernelBiomeView.java", Files.readString(HOOK_SOURCE));
		return InjectorExecution.compile(work, sources);
	}

	/** One biome through the pass: built with a temperate climate, rained on by a Fabric mod, fogged by NeoForge's modifier. */
	private static Object modified(ClassLoader loader) throws Throwable {
		Class<?> climate = loader.loadClass(BIOME + "$ClimateSettings");
		Object temperate = InjectorExecution.construct(climate, 0.8f, 0.4f);
		Object effects = InjectorExecution.construct(loader.loadClass("net.minecraft.world.level.biome.BiomeSpecialEffects"), 0xC0D8FF);
		Object biome = InjectorExecution.construct(loader.loadClass(BIOME), temperate, effects);
		Class<?> infoType = loader.loadClass(INFO + "$BiomeInfo");
		Object original = InjectorExecution.construct(infoType, temperate, effects, "features", "spawns");
		InjectorExecution.invoke(biome, "replaceClimate", InjectorExecution.construct(climate, 0.8f, 0.9f));

		Object holder = InjectorExecution.construct(loader.loadClass("net.minecraft.core.Holder$Direct"), biome, "minecraft:plains");
		UnaryOperator<Object> fog = info -> {
			try {
				return InjectorExecution.construct(infoType, InjectorExecution.invoke(info, "climateSettings"),
						InjectorExecution.construct(effects.getClass(), 0x101010), InjectorExecution.invoke(info, "generationSettings"),
						InjectorExecution.invoke(info, "mobSpawnSettings"));
			} catch (Throwable failed) {
				throw new AssertionError(failed);
			}
		};
		Object modifiable = InjectorExecution.construct(loader.loadClass(INFO), original);
		assertEquals(true, InjectorExecution.invoke(modifiable, "applyBiomeModifiers", holder, List.of(fog),
				java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] {loader.loadClass("net.minecraft.core.RegistryAccess")},
						(proxy, method, args) -> null)));
		return modifiable.getClass().getField("modifiedBiomeInfo").get(modifiable);
	}

	@Test void theModifierPassStartsFromTheFabricClimate(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = compile(work);
		String internal = INFO.replace('.', '/');
		byte[] rebased = InjectorExecution.transform(new BiomeInfoRebaseInjector(), INFO, original.get(internal), EnvType.SERVER);
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(internal, rebased);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(rebased, loader));

		assertEquals("BiomeInfo[climateSettings=ClimateSettings[temperature=0.8, downfall=0.9], "
				+ "effects=BiomeSpecialEffects[fogColor=1052688], generationSettings=features, mobSpawnSettings=spawns]",
				String.valueOf(modified(loader)), "the Fabric mod's rain survives, and NeoForge's fog applies on top");
		assertEquals("BiomeInfo[climateSettings=ClimateSettings[temperature=0.8, downfall=0.4], "
				+ "effects=BiomeSpecialEffects[fogColor=1052688], generationSettings=features, mobSpawnSettings=spawns]",
				String.valueOf(modified(InjectorExecution.load(original))),
				"premise: as merged, the pass starts from the constructor's climate and the rain is gone");
		assertSame(rebased, InjectorExecution.transform(new BiomeInfoRebaseInjector(), INFO, rebased, EnvType.SERVER),
				"an already rebased pass is left alone");
	}

	@Test void theDiagnosticSwitchLeavesThePassAsMerged(@TempDir Path work) throws Exception {
		byte[] bytes = compile(work).get(INFO.replace('.', '/'));
		System.setProperty(BiomeInfoRebaseInjector.DIAGNOSTIC, "off");
		assertSame(bytes, InjectorExecution.transform(new BiomeInfoRebaseInjector(), INFO, bytes, EnvType.SERVER));
	}
}
