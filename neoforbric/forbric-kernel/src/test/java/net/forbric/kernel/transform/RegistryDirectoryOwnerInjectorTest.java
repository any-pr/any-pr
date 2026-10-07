/*
 * Copyright 2026 The Forbric Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.forbric.api.DiscoveredMod;
import net.forbric.api.Ecosystem;
import net.forbric.api.ModPresence;
import net.forbric.kernel.boot.KernelRegistryDirectories;
import net.forbric.kernel.mixin.MergedBaseMixinCompat;

/**
 * WorldWeaver's world presets and biome data are read from the directory WorldWeaver ships them in, as on native
 * Fabric.
 *
 * <p>Real bytes throughout, wherever they exist on this machine: {@code Registries}' directory methods from vanilla
 * 26.2, from the merged base and from MinecraftForge's patched game; NeoForge's own {@code prefixNamespace}; and the
 * two mixin handlers that decide the outcome on native Fabric — fabric-registry-sync's {@code RegistriesMixin} and
 * WorldWeaver's {@code RegistryDataLoaderMixinEarly} — out of the sweep pack's jars. Only {@code Identifier},
 * {@code ResourceKey} and wover's "is this one of my registries" lookup are stand-ins, because the real ones drag in
 * DataFixerUpper and wover's whole registry. The directory each arm answers is then checked against the files
 * WorldWeaver's jar actually ships, which is exactly what the content census counted: 5+3 presets and 10 biome
 * entries on native Fabric, none on Forbric.
 */
class RegistryDirectoryOwnerInjectorTest {
	private static final Path STAGED = TestFixtures.stagedRoot();
	private static final Path MERGED_BASE = STAGED.resolve("neoforge-base/patched-mc-neoforge-26.2.jar");
	private static final Path FORGE_PATCHED = STAGED.resolve("forge-patched/patched-mc-forge-26.2.jar");
	private static final Path NEO_RUNTIME = STAGED.resolve("neoforge-runtime/neoforge-runtime.jar");
	private static final Path VANILLA = TestFixtures.vanillaJar();
	private static final Path SWEEP = Path.of("build/compat-inputs/sweep90/mods");
	private static final Path FABRIC_API = SWEEP.resolve("fabric-api-0.161.0+26.2.jar");
	private static final Path WORLDWEAVER = SWEEP.resolve("worldweaver-26.201.2.jar");

	private static final String REGISTRIES = "net/minecraft/core/registries/Registries";
	private static final String COMMON_HOOKS = "net/neoforged/neoforge/common/CommonHooks";
	private static final String IDENTIFIER = "net/minecraft/resources/Identifier";
	private static final String RESOURCE_KEY = "net/minecraft/resources/ResourceKey";
	private static final String FABRIC_MIXIN = "net/fabricmc/fabric/mixin/registry/sync/RegistriesMixin";
	private static final String WOVER_MIXIN = "de/ambertation/wover/core/mixin/registry/RegistryDataLoaderMixinEarly";
	private static final String WOVER_BUILDER = "de/ambertation/wover/core/impl/registry/DatapackRegistryBuilderImpl";
	private static final String MIXIN_MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
	private static final String NOT_IN_REGISTRIES = "RegistriesMixin is not in Registries";
	private static final Set<String> DIR_METHODS = Set.of("registryDirPath", "elementsDirPath", "tagsDirPath",
			"componentsDirPath");

	private static final String PRESET_INFO = "wover/world_preset_info";
	private static final String BIOME_DATA = "wover/worldgen/biome_data";

	@BeforeEach
	@AfterEach
	void reset() {
		System.clearProperty(RegistryDirectoryOwnerInjector.PROPERTY);
		ModPresence.publishForgeFamily(List.of());
		ModPresence.publishFabric(List.of());
		// The arms run fabric-registry-sync's modifier beside Registries rather than merged into it, so the hook is
		// told it is there; the tests of that question below build a Registries that carries it, or does not.
		KernelRegistryDirectories.resetForTests(Boolean.TRUE);
	}

	@Test
	void theMergedBodyIsNeoForgesAndTheEditHandsEveryAnswerToTheOwner() throws Exception {
		TestFixtures.require(Fixture.STAGED, Files.isRegularFile(MERGED_BASE), "staged merged base absent — skipping real-bytecode check");
		byte[] in = classBytes(MERGED_BASE, REGISTRIES);
		assertTrue(calls(method(node(in), "registryDirPath"), COMMON_HOOKS, "prefixNamespace"),
				"the merged body prefixes through NeoForge's hook — if it stopped, re-derive the repair");

		byte[] out = transform(in);
		assertNotSame(in, out);
		ClassNode node = node(out);
		MethodNode body = method(node, "registryDirPath");
		assertTrue(calls(body, RegistryDirectoryOwnerInjector.HOOK_OWNER, "registryDirPath"));
		assertTrue(body.maxStack >= 3, "namespace and path ride on top of the merged answer");
		new Analyzer<>(new BasicVerifier()).analyze(node.name, body);
		assertSame(out, transform(out), "a second pass must not wrap the answer twice");
	}

	@Test
	void woversRegistriesReadTheDirectoryWoverShipsAsOnNativeFabric() throws Exception {
		requireAll(VANILLA, MERGED_BASE, NEO_RUNTIME, FABRIC_API, WORLDWEAVER);
		ModPresence.publishFabric(List.of(mod(Ecosystem.FABRIC, "wover")));

		Arm nativeFabric = arm(registries(VANILLA, false));
		Arm before = arm(registries(MERGED_BASE, false));
		Arm after = arm(registries(MERGED_BASE, true));

		// Native Fabric: wover's own mixin hands back vanilla's body before fabric-registry-sync can prefix it.
		assertEquals(PRESET_INFO, nativeFabric.elements("wover", PRESET_INFO));
		assertEquals(Map.of("minecraft", 5, "wover", 3), shipped(nativeFabric.elements("wover", PRESET_INFO)),
				"the census's native-Fabric count: five vanilla presets and wover's own three");
		assertEquals(Map.of("minecraft", 10), shipped(nativeFabric.elements("wover", BIOME_DATA)));

		// The merged body prefixed already, so wover handed back a directory nothing ships.
		assertEquals("wover/" + PRESET_INFO, before.elements("wover", PRESET_INFO));
		assertEquals(Map.of(), shipped(before.elements("wover", PRESET_INFO)), "the census's Forbric count");
		assertEquals(Map.of(), shipped(before.elements("wover", BIOME_DATA)));

		for (String registry : List.of(PRESET_INFO, BIOME_DATA)) {
			assertEquals(nativeFabric.elements("wover", registry), after.elements("wover", registry), registry);
			assertEquals(shipped(nativeFabric.elements("wover", registry)), shipped(after.elements("wover", registry)));
			// wover's mixin leaves tagsDirPath alone, so fabric-registry-sync prefixes its tags on both.
			assertEquals("tags/wover/" + registry, nativeFabric.tags("wover", registry));
			assertEquals(nativeFabric.tags("wover", registry), after.tags("wover", registry), registry);
		}
	}

	@Test
	void everyOtherRegistryEndsWhereItsOwnLoaderPutsIt() throws Exception {
		requireAll(VANILLA, MERGED_BASE, NEO_RUNTIME, FABRIC_API, WORLDWEAVER);
		ModPresence.publishFabric(List.of(mod(Ecosystem.FABRIC, "wover"), mod(Ecosystem.FABRIC, "owo"),
				mod(Ecosystem.FABRIC, "cloth-config")));
		ModPresence.publishForgeFamily(List.of(mod(Ecosystem.NEOFORGE, "examplemod"),
				mod(Ecosystem.NEOFORGE, "cloth_config")));

		Arm nativeFabric = arm(registries(VANILLA, false));
		Arm before = arm(registries(MERGED_BASE, false));
		Arm after = arm(registries(MERGED_BASE, true));

		// A plain Fabric registry: vanilla body now, and fabric-registry-sync puts the namespace back as natively.
		assertEquals("owo_things", after.body("elementsDirPath", "owo", "owo_things"));
		assertEquals("owo/owo_things", after.elements("owo", "owo_things"));
		assertEquals(nativeFabric.elements("owo", "owo_things"), after.elements("owo", "owo_things"));
		assertEquals(before.elements("owo", "owo_things"), after.elements("owo", "owo_things"));
		assertEquals(before.tags("owo", "owo_things"), after.tags("owo", "owo_things"));

		// A NeoForge mod's registry, one no mod claims, one two ecosystems claim, and vanilla's: the merged answer.
		for (String[] id : new String[][] {{"examplemod", "thing"}, {"unclaimed", "thing"}, {"cloth_config", "thing"},
				{"minecraft", "worldgen/biome"}}) {
			assertEquals(before.body("elementsDirPath", id[0], id[1]), after.body("elementsDirPath", id[0], id[1]),
					id[0]);
			assertEquals(before.body("tagsDirPath", id[0], id[1]), after.body("tagsDirPath", id[0], id[1]), id[0]);
		}
		assertEquals("examplemod/thing", after.body("elementsDirPath", "examplemod", "thing"));
		assertEquals("worldgen/biome", after.body("elementsDirPath", "minecraft", "worldgen/biome"));
		// componentsDirPath is datagen's, and Fabric never prefixed it.
		assertEquals(nativeFabric.body("componentsDirPath", "owo", "owo_things"),
				after.body("componentsDirPath", "owo", "owo_things"));
	}

	@Test
	void minecraftForgesInlineBodyIsEditedAtBothOfItsReturns() throws Exception {
		requireAll(FORGE_PATCHED);
		ModPresence.publishFabric(List.of(mod(Ecosystem.FABRIC, "wover")));
		byte[] in = classBytes(FORGE_PATCHED, REGISTRIES);
		MethodNode original = method(node(in), "registryDirPath");
		assertEquals(2, count(original, Opcodes.ARETURN), "MinecraftForge prefixes inline, with its own return");

		Arm forge = arm(registries(FORGE_PATCHED, true));
		assertEquals(PRESET_INFO, forge.body("elementsDirPath", "wover", PRESET_INFO));
		assertEquals("examplemod/thing", forge.body("elementsDirPath", "examplemod", "thing"));
		assertEquals("worldgen/biome", forge.body("elementsDirPath", "minecraft", "worldgen/biome"));
	}

	@Test
	void switchedOffRegistriesStaysAsMerged() throws Exception {
		TestFixtures.require(Fixture.STAGED, Files.isRegularFile(MERGED_BASE), "staged merged base absent");
		System.setProperty(RegistryDirectoryOwnerInjector.PROPERTY, "off");
		byte[] in = classBytes(MERGED_BASE, REGISTRIES);
		assertSame(in, transform(in));
		assertTrue(new RegistryDirectoryOwnerInjector().anchors().anchors().isEmpty());
	}

	@Test
	void otherClassesAreLeftAlone() {
		byte[] bytes = {(byte) 0xCA, (byte) 0xFE};
		assertSame(bytes, new RegistryDirectoryOwnerInjector().transform("net.minecraft.core.Registry", bytes, null));
	}

	@Test
	void theHookFallsBackToTheMergedAnswerRatherThanThrowing() {
		ModPresence.publishFabric(List.of(mod(Ecosystem.FABRIC, "wover")));
		assertEquals("wover/x", KernelRegistryDirectories.registryDirPath("wover/x", null, "x"));
		assertEquals("wover/x", KernelRegistryDirectories.registryDirPath("wover/x", "wover", null));
		assertEquals(null, KernelRegistryDirectories.registryDirPath(null, "unclaimed", "x"));
		assertEquals("x", KernelRegistryDirectories.registryDirPath(null, "wover", "x"));
		ModPresence.publishFabric(List.of());
		assertEquals("wover/x", KernelRegistryDirectories.registryDirPath("wover/x", "wover", "x"),
				"before the Fabric mods are published, every registry keeps the merged answer");
	}

	@Test
	void aRegistriesThatTookFabricsModifierHandsFabricRegistriesVanillasDirectory() throws Exception {
		requireAll(VANILLA, MERGED_BASE, NEO_RUNTIME, FABRIC_API, WORLDWEAVER);
		ModPresence.publishFabric(List.of(mod(Ecosystem.FABRIC, "wover")));
		Arm nativeFabric = arm(registries(VANILLA, false));
		KernelRegistryDirectories.resetForTests(null);
		Arm after = arm(withFabricsModifier(registries(MERGED_BASE, true)));

		String log = capture(() -> {
			for (String registry : List.of(PRESET_INFO, BIOME_DATA)) {
				assertEquals(nativeFabric.elements("wover", registry), after.elements("wover", registry), registry);
			}
		});
		assertFalse(log.contains(NOT_IN_REGISTRIES), log);
	}

	@Test
	void withoutFabricsModifierInRegistriesEveryRegistryKeepsTheMergedDirectory() throws Exception {
		requireAll(MERGED_BASE, NEO_RUNTIME, FABRIC_API, WORLDWEAVER);
		ModPresence.publishFabric(List.of(mod(Ecosystem.FABRIC, "wover"), mod(Ecosystem.FABRIC, "owo")));
		Arm before = arm(registries(MERGED_BASE, false));
		KernelRegistryDirectories.resetForTests(null);
		// Mixin never merged fabric-registry-sync's mixin into this one: pinned, dropped as unfit, or not installed.
		Arm after = arm(registries(MERGED_BASE, true));

		String log = capture(() -> {
			for (String registry : List.of(PRESET_INFO, BIOME_DATA)) {
				assertEquals(before.elements("wover", registry), after.elements("wover", registry), registry);
			}
			// vanilla's path here would have nothing to put the namespace back
			assertEquals("owo/owo_things", after.body("elementsDirPath", "owo", "owo_things"));
			assertEquals("tags/owo/owo_things", after.body("tagsDirPath", "owo", "owo_things"));
		});
		assertEquals(1, log.split(NOT_IN_REGISTRIES, -1).length - 1, "one WARN, however many registries ask: " + log);
	}

	@Test
	void forceHandsFabricRegistriesVanillasDirectoryWithoutAsking() throws Exception {
		requireAll(VANILLA, MERGED_BASE, NEO_RUNTIME, FABRIC_API, WORLDWEAVER);
		System.setProperty(RegistryDirectoryOwnerInjector.PROPERTY, "force");
		ModPresence.publishFabric(List.of(mod(Ecosystem.FABRIC, "wover")));
		Arm nativeFabric = arm(registries(VANILLA, false));
		KernelRegistryDirectories.resetForTests(null);
		Arm after = arm(registries(MERGED_BASE, true));

		for (String registry : List.of(PRESET_INFO, BIOME_DATA)) {
			assertEquals(nativeFabric.elements("wover", registry), after.elements("wover", registry), registry);
		}
	}

	@Test
	void fabricsModifierIsNotPinnedOffTheMergedBase() {
		// Pinned, it would not crash anything: the hook finds it missing from Registries, every registry keeps the
		// merged directory, and WorldWeaver's world presets and biome data load empty again. That cost is the pin's.
		String mixin = "fabric-registry-sync-v0.mixins.json:RegistriesMixin";
		assertFalse(MergedBaseMixinCompat.SUPPRESSED_MIXINS.contains(mixin), mixin);
		assertFalse(MergedBaseMixinCompat.SUPPRESSED_UNLESS_PRUNED.contains(mixin), mixin);
		assertFalse(MergedBaseMixinCompat.DISABLED_CONFIGS.contains("fabric-registry-sync-v0.mixins.json"));
	}

	// --- one arm: Registries' directory methods, plus the two mixins as Mixin lays them out on native Fabric ---

	private record Arm(Class<?> registries, Class<?> identifier, Class<?> resourceKey, Method fabricElements,
			Method fabricTags, Method wover) {
