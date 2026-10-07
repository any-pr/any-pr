/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.api.EnvType;
import net.forbric.api.DiscoveredMod;
import net.forbric.api.Ecosystem;
import net.forbric.api.ModPresence;
import net.forbric.kernel.boot.KernelRegistryDirectories;

/**
 * {@link RegistryDirectoryOwnerInjector}'s output, run against the kernel's real boot-side
 * {@code KernelRegistryDirectories}: {@code Registries.registryDirPath} answers a Fabric mod's registry with vanilla's
 * directory, which fabric-registry-sync's modifier then prefixes (or WorldWeaver's own mixin keeps as it is) — where as
 * merged NeoForge's body prefixed it first, and WorldWeaver's world presets and biome data were read from a directory
 * nothing ships. A NeoForge mod's registry and vanilla's keep the merged answer, and so does every registry when
 * fabric-registry-sync's modifier is not in {@code Registries}.
 *
 * <p>The stand-in {@code Registries} has NeoForge's body (the namespace put in front by {@code CommonHooks}) and, as
 * Mixin leaves a merged member, a method marked {@code @MixinMerged} from fabric-registry-sync's
 * {@code RegistriesMixin} — the mark written into the class file, as Mixin writes it, since the annotation cannot be
 * applied in source: the hook reads that mark off the calling class. Mod ownership comes from {@code ModPresence},
 * published as a boot publishes it.
 */
@ExecutesInjector(RegistryDirectoryOwnerInjector.class)
@ResourceLock("system-properties")
@ResourceLock("ModPresence")
class RegistryDirectoryOwnerInjectorExecutionTest {
	private static final String REGISTRIES = RegistryDirectoryOwnerInjector.TARGET;

	private static final Map<String, String> STAND_INS = Map.of(
			"net.minecraft.resources.Identifier", """
					package net.minecraft.resources;

					public final class Identifier {
						private final String namespace;
						private final String path;

						public Identifier(String namespace, String path) {
							this.namespace = namespace;
							this.path = path;
						}

						public String getNamespace() {
							return namespace;
						}

						public String getPath() {
							return path;
						}
					}
					""",
			"net.minecraft.resources.ResourceKey", """
					package net.minecraft.resources;

					public final class ResourceKey<T> {
						private final Identifier identifier;

						public ResourceKey(Identifier identifier) {
							this.identifier = identifier;
						}

						public Identifier identifier() {
							return identifier;
						}
					}
					""",
			"net.neoforged.neoforge.common.CommonHooks", """
					package net.neoforged.neoforge.common;

					import net.minecraft.resources.Identifier;

					public class CommonHooks {
						public static String prefixNamespace(Identifier id) {
							return id.getNamespace().equals("minecraft") ? id.getPath() : id.getNamespace() + "/" + id.getPath();
						}
					}
					""",
			REGISTRIES, """
					package net.minecraft.core.registries;

					import net.minecraft.resources.ResourceKey;
					import net.neoforged.neoforge.common.CommonHooks;

					public class Registries {
						/** NeoForge's body. */
						public static String registryDirPath(ResourceKey<?> key) {
							return CommonHooks.prefixNamespace(key.identifier());
						}

						/** fabric-registry-sync's return-value modifier as Mixin merges it; the @MixinMerged mark is added in the class file. */
						private static String fabric_prefixDirectory(String path) {
							return path;
						}
					}
					""");

	@BeforeEach @AfterEach void reset() {
		System.clearProperty(RegistryDirectoryOwnerInjector.PROPERTY);
		ModPresence.publishForgeFamily(List.of());
		ModPresence.publishFabric(List.of());
		// Unknown, as on a fresh boot: the hook asks the calling Registries whether fabric-registry-sync's modifier is in it.
		KernelRegistryDirectories.resetForTests(null);
	}

	private static DiscoveredMod mod(Ecosystem ecosystem, String id) {
		return new DiscoveredMod(ecosystem, id, "1.0.0", id, List.of(), List.of(), null, id + ".jar");
	}

	/** A pack with WorldWeaver on Fabric and a NeoForge mod. */
	private static void publish() {
		ModPresence.publishFabric(List.of(mod(Ecosystem.FABRIC, "wover")));
		ModPresence.publishForgeFamily(List.of(mod(Ecosystem.NEOFORGE, "examplemod")));
	}

	/** Registries as Mixin leaves it after fabric-registry-sync's modifier: the merged member carries {@code @MixinMerged}. */
	private static Map<String, byte[]> withFabricModifier(Map<String, byte[]> compiled) {
		String internal = REGISTRIES.replace('.', '/');
		ClassNode node = new ClassNode();
		new ClassReader(compiled.get(internal)).accept(node, 0);
		org.objectweb.asm.tree.MethodNode merged = node.methods.stream().filter(m -> m.name.equals("fabric_prefixDirectory")).findFirst().orElseThrow();
		org.objectweb.asm.tree.AnnotationNode mark = new org.objectweb.asm.tree.AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
		mark.values = List.of("mixin", "net.fabricmc.fabric.mixin.registry.sync.RegistriesMixin", "priority", 1000);
		merged.visibleAnnotations = List.of(mark);
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		Map<String, byte[]> classes = new HashMap<>(compiled);
		classes.put(internal, writer.toByteArray());
		return classes;
	}

	private static String dir(ClassLoader loader, String namespace, String path) throws Throwable {
		Object id = InjectorExecution.construct(loader.loadClass("net.minecraft.resources.Identifier"), namespace, path);
		Object key = InjectorExecution.construct(loader.loadClass("net.minecraft.resources.ResourceKey"), id);
		return (String) InjectorExecution.invokeStatic(loader.loadClass(REGISTRIES), "registryDirPath", key);
	}

	private static Map<String, byte[]> owned(Map<String, byte[]> original) {
		String internal = REGISTRIES.replace('.', '/');
		byte[] out = InjectorExecution.transform(new RegistryDirectoryOwnerInjector(), REGISTRIES, original.get(internal), EnvType.SERVER);
		assertNotSame(original.get(internal), out, "every return was handed to the owner");
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(internal, out);
		return classes;
	}

	@Test void aFabricRegistryGetsVanillasDirectoryForItsOwnersMixins(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = withFabricModifier(InjectorExecution.compile(work, STAND_INS));
		Map<String, byte[]> classes = owned(original);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(classes.get(REGISTRIES.replace('.', '/')), loader));
		publish();

		assertEquals("worldgen/world_preset", dir(loader, "wover", "worldgen/world_preset"),
				"WorldWeaver's registry gets vanilla's directory, as on native Fabric");
		assertEquals("examplemod/machines", dir(loader, "examplemod", "machines"), "a NeoForge mod's registry keeps NeoForge's");
		assertEquals("worldgen/biome", dir(loader, "minecraft", "worldgen/biome"));

		assertEquals("wover/worldgen/world_preset", dir(InjectorExecution.load(original), "wover", "worldgen/world_preset"),
				"premise: as merged, NeoForge's body prefixed the Fabric registry before its owner's mixins could decide");
		byte[] once = classes.get(REGISTRIES.replace('.', '/'));
		assertSame(once, InjectorExecution.transform(new RegistryDirectoryOwnerInjector(), REGISTRIES, once, EnvType.SERVER), "edited once");
	}

	@Test void withoutFabricRegistrySyncsModifierEveryRegistryKeepsTheMergedDirectory(@TempDir Path work) throws Throwable {
		// The same Registries, but nothing in it carries fabric-registry-sync's @MixinMerged mark.
		Map<String, byte[]> without = InjectorExecution.compile(work, STAND_INS);
		publish();
		assertEquals("wover/worldgen/world_preset", dir(InjectorExecution.load(owned(without)), "wover", "worldgen/world_preset"),
				"nothing would put the namespace back, so the hook keeps the merged answer");
	}

	@Test void switchedOffRegistriesIsLeftAsMerged(@TempDir Path work) throws Exception {
		byte[] bytes = InjectorExecution.compile(work, STAND_INS).get(REGISTRIES.replace('.', '/'));
		System.setProperty(RegistryDirectoryOwnerInjector.PROPERTY, "off");
		assertSame(bytes, InjectorExecution.transform(new RegistryDirectoryOwnerInjector(), REGISTRIES, bytes, EnvType.SERVER));
	}
}
