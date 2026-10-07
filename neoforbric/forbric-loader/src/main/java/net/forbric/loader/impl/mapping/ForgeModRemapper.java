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

package net.forbric.loader.impl.mapping;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import net.forbric.loader.impl.metadata.UnifiedDependency;
import net.forbric.loader.impl.util.ForbricLog;

/**
 * Remaps a Forge/NeoForge mod jar from the Mojang "named" (Mojmap) namespace it was compiled against to
 * Forbric's canonical runtime namespace (intermediary), using {@link ForbricMappings} to drive tiny-remapper.
 *
 * <p>This is the engine behind the {@code DEOBF_REMAP} transform phase: after this, a Forge mod's bytecode
 * references the game by the same intermediary names the Fabric substrate runs in, so its classes link and
 * load in the same instance. tiny-remapper resolves inheritance from the game/library jars passed as the
 * remap classpath (in the named namespace), so inherited members map correctly too.
 */
public final class ForgeModRemapper {
	private ForgeModRemapper() {
	}

	/**
	 * Builds a tiny-remapper mapping provider that renames a Forge mod from the named (Mojmap) namespace to
	 * intermediary, derived from the merged mapping tree.
	 */
	public static IMappingProvider mappingProvider(ForbricMappings mappings) {
		return provider(mappings, ForbricMappings.NAMED, ForbricMappings.INTERMEDIARY);
	}

	/**
	 * Builds a tiny-remapper provider for an arbitrary namespace pair held in the merged tree
	 * ({@code named}, {@code official}, {@code intermediary}). For example {@code provider(m, "official", "named")}
	 * produces the mapping that deobfuscates the vanilla game jar to Mojmap (used to build the remap classpath).
	 */
	public static IMappingProvider provider(ForbricMappings mappings, String fromNs, String toNs) {
		MappingTree tree = mappings.tree();
		String srcNs = tree.getSrcNamespace();

		boolean fromIsSrc = fromNs.equals(srcNs);
		boolean toIsSrc = toNs.equals(srcNs);
		int fromId = fromIsSrc ? -1 : tree.getNamespaceId(fromNs);
		int toId = toIsSrc ? -1 : tree.getNamespaceId(toNs);

		if (!fromIsSrc && fromId < 0) throw new IllegalArgumentException("unknown source namespace: " + fromNs);
		if (!toIsSrc && toId < 0) throw new IllegalArgumentException("unknown target namespace: " + toNs);

		return acceptor -> {
			for (MappingTree.ClassMapping cls : tree.getClasses()) {
				String src = fromIsSrc ? cls.getSrcName() : cls.getName(fromId);
				String dst = toIsSrc ? cls.getSrcName() : cls.getName(toId);
				if (src == null || dst == null) continue;

				acceptor.acceptClass(src, dst);

				for (MappingTree.FieldMapping field : cls.getFields()) {
					String fieldDst = toIsSrc ? field.getSrcName() : field.getName(toId);
					String fieldName = fromIsSrc ? field.getSrcName() : field.getName(fromId);
					String fieldDesc = fromIsSrc ? field.getSrcDesc() : field.getDesc(fromId);
					if (fieldDst == null || fieldName == null) continue;

					acceptor.acceptField(new IMappingProvider.Member(src, fieldName, fieldDesc), fieldDst);
				}

				for (MappingTree.MethodMapping method : cls.getMethods()) {
					String methodDst = toIsSrc ? method.getSrcName() : method.getName(toId);
					String methodName = fromIsSrc ? method.getSrcName() : method.getName(fromId);
					String methodDesc = fromIsSrc ? method.getSrcDesc() : method.getDesc(fromId);
					if (methodDst == null || methodName == null) continue;

					acceptor.acceptMethod(new IMappingProvider.Member(src, methodName, methodDesc), methodDst);
				}
			}
		};
	}

	/**
	 * Remaps {@code input} to {@code output}, mapping Mojmap → intermediary. Non-class files are copied through.
	 *
	 * @param remapClasspath the game + library jars (in the named namespace) used for inheritance resolution;
	 *                       may be empty for simple mods that only reference declared members directly.
	 */
	public static void remapJar(Path input, Path output, ForbricMappings mappings, List<Path> remapClasspath) throws IOException {
		remapJar(input, output, mappingProvider(mappings), remapClasspath);
	}

	/** Remaps {@code input} to {@code output} using an explicit provider (e.g. {@code provider(m, "official", "named")}). */
	public static void remapJar(Path input, Path output, IMappingProvider provider, List<Path> remapClasspath) throws IOException {
		Files.deleteIfExists(output);

		TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(provider)
				.renameInvalidLocals(false)
				.threads(1)
				.build();

		try (OutputConsumerPath out = new OutputConsumerPath.Builder(output).build()) {
			out.addNonClassFiles(input);

			if (remapClasspath != null) {
				for (Path cp : remapClasspath) {
					remapper.readClassPath(cp);
				}
			}

			remapper.readInputs(input);
			remapper.apply(out);
		} finally {
			remapper.finish();
		}
	}

	/**
	 * Single-{@code @Mod} convenience: see {@link #wrapAsFabricMod(Path, String, String, List, List)}.
	 */
	public static void wrapAsFabricMod(Path remappedJar, String modId, String version, String forgeClass) throws IOException {
		wrapAsFabricMod(remappedJar, modId, version, java.util.Collections.singletonList(forgeClass), java.util.Collections.emptyList());
	}

	/**
	 * Injects a synthetic {@code fabric.mod.json} into a (wrapped) Forge/NeoForge mod jar so the Fabric
	 * substrate (Knot) discovers and loads its classes like any other mod. The mod's {@code @Mod} classes are
	 * recorded under the {@code forbric:forgeClasses} custom key (and the first under {@code forbric:forgeClass});
	 * the Knot-loaded NeoForge runtime driver ({@code ForbricNeoForgeRuntime}) discovers them from those keys and
	 * brings each up — the wrapped jar itself declares NO entrypoint. The mod's own Mixin configs are listed in
	 * the {@code "mixins"} array so the substrate's {@code FabricMixinBootstrap} registers them.
	 *
	 * @param remappedJar  a jar already remapped to the runtime (intermediary) namespace
	 * @param modId        the (sanitized) Fabric mod id
	 * @param version      the mod version (should be SemVer-parseable; non-SemVer is tolerated with a warning)
	 * @param forgeClasses the Forge mod's {@code @Mod} classes (binary names) to bring up at init
	 * @param mixinConfigs the mod's Mixin config resource names to register, or empty
	 */
	public static void wrapAsFabricMod(Path remappedJar, String modId, String version,
			List<String> forgeClasses, List<String> mixinConfigs) throws IOException {
		wrapAsFabricMod(remappedJar, modId, version, forgeClasses, mixinConfigs, java.util.Collections.emptyList());
	}

	/** Platform/loader ids a Forge dependency may name that have no Forbric mod to resolve against — skipped. */
	private static final Set<String> PLATFORM_DEP_IDS = Set.of(
			"neoforge", "forge", "fml", "java", "minecraft", "mixinextras", "fabricloader", "fabric");

	/**
	 * As {@link #wrapAsFabricMod(Path, String, String, List, List)}, additionally translating the Forge mod's
	 * {@code [[dependencies]]} into Fabric {@code depends} (mandatory) / {@code recommends} (optional) so they enter
	 * the SAME sat4j solve as every other mod (a missing hard dependency is then a real resolution error, not a
	 * late {@code LinkageError}). Platform ids ({@code neoforge}/{@code minecraft}/… — {@link #PLATFORM_DEP_IDS})
	 * are skipped: there is no corresponding Forbric mod to resolve them against. Version constraints are already
	 * Fabric predicates ({@link UnifiedDependency}, translated from the Maven range at discovery time).
	 */
	public static void wrapAsFabricMod(Path remappedJar, String modId, String version,
			List<String> forgeClasses, List<String> mixinConfigs, List<UnifiedDependency> dependencies) throws IOException {
		wrapAsFabricMod(remappedJar, modId, version, forgeClasses, mixinConfigs, dependencies,
				java.util.Collections.emptyList());
	}

	/**
	 * As above, additionally referencing JarJar-nested library jars via Fabric's {@code "jars"} mechanism
	 * (each already carries a fabric.mod.json — see {@code JarJarTranslator}).
	 */
	public static void wrapAsFabricMod(Path remappedJar, String modId, String version,
			List<String> forgeClasses, List<String> mixinConfigs, List<UnifiedDependency> dependencies,
			List<String> nestedJarPaths) throws IOException {
		wrapAsFabricMod(remappedJar, modId, version, forgeClasses, mixinConfigs, dependencies, nestedJarPaths, null);
	}

	/**
	 * As above, but with {@code presentModIds} = the set of mod ids actually present this boot (Fabric + wrapped
	 * Forge). A mandatory Forge dependency is emitted as a hard Fabric {@code depends} ONLY when a provider for it
	 * is present; a mandatory dependency with NO present provider is downgraded to {@code recommends} (+ a warning)
	 * instead. Otherwise a single missing hard dependency makes Fabric's sat4j solve unsatisfiable and aborts the
	 * ENTIRE launch (every Fabric AND Forge mod), which is a terrible failure mode for "load most mods". Passing
	 * {@code null} keeps the strict behavior (every mandatory dep → {@code depends}).
	 */
	public static void wrapAsFabricMod(Path remappedJar, String modId, String version,
			List<String> forgeClasses, List<String> mixinConfigs, List<UnifiedDependency> dependencies,
			List<String> nestedJarPaths, Set<String> presentModIds) throws IOException {
		wrapAsFabricMod(remappedJar, modId, version, forgeClasses, mixinConfigs, dependencies, nestedJarPaths,
				presentModIds, net.forbric.loader.impl.metadata.ModEcosystem.NEOFORGE);
	}

	/**
	 * As above, but stamping which Forge-family ecosystem the wrap belongs to under the
	 * {@code forbric:ecosystem} custom key ({@code "neoforge"}), so the Knot-loaded runtime
	 * driver (and the synthetic module layer) only picks up its own family's mods. A wrap without the key
	 * (produced before this format) matches whichever single runtime is present.
	 */
	public static void wrapAsFabricMod(Path remappedJar, String modId, String version,
			List<String> forgeClasses, List<String> mixinConfigs, List<UnifiedDependency> dependencies,
			List<String> nestedJarPaths, Set<String> presentModIds,
			net.forbric.loader.impl.metadata.ModEcosystem ecosystem) throws IOException {
		Map<String, String> depends = new LinkedHashMap<>();
		Map<String, String> recommends = new LinkedHashMap<>();

		if (dependencies != null) {
			for (UnifiedDependency dep : dependencies) {
				if (dep.getModId() == null || PLATFORM_DEP_IDS.contains(dep.getModId().toLowerCase())) continue;

				boolean mandatory = dep.isMandatory();
				if (mandatory && presentModIds != null && !presentModIds.contains(dep.getModId().toLowerCase())) {
					ForbricLog.warn("[Forbric] %s: required dependency '%s' has no provider present — treating it as"
							+ " optional so the rest of your mods still load (this mod may fail at runtime if it truly"
							+ " needs it; install '%s' to silence this).", modId, dep.getModId(), dep.getModId());
					mandatory = false;
				}
				(mandatory ? depends : recommends).put(dep.getModId(), dep.getVersionConstraint());
			}
		}

		StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"schemaVersion\": 1,\n");
		json.append("  \"id\": \"").append(modId).append("\",\n");
		json.append("  \"version\": \"").append(version).append("\",\n");
		json.append("  \"name\": \"").append(modId).append(" (Forge via Forbric)\",\n");
		json.append("  \"environment\": \"*\",\n");

		if (mixinConfigs != null && !mixinConfigs.isEmpty()) {
			json.append("  \"mixins\": ").append(jsonStringArray(mixinConfigs)).append(",\n");
		}
		if (nestedJarPaths != null && !nestedJarPaths.isEmpty()) {
			json.append("  \"jars\": [");
			for (int i = 0; i < nestedJarPaths.size(); i++) {
				if (i > 0) json.append(", ");
				json.append("{ \"file\": \"").append(nestedJarPaths.get(i)).append("\" }");
			}
			json.append("],\n");
		}
		if (!depends.isEmpty()) {
			json.append("  \"depends\": ").append(jsonStringObject(depends)).append(",\n");
		}
		if (!recommends.isEmpty()) {
			json.append("  \"recommends\": ").append(jsonStringObject(recommends)).append(",\n");
		}

		String first = forgeClasses == null || forgeClasses.isEmpty() ? "" : forgeClasses.get(0);
		String family = (ecosystem == null ? net.forbric.loader.impl.metadata.ModEcosystem.NEOFORGE : ecosystem).familyId();
		json.append("  \"custom\": {\n");
		json.append("    \"forbric:forgeClass\": \"").append(first).append("\",\n");
		json.append("    \"forbric:forgeClasses\": ").append(jsonStringArray(forgeClasses)).append(",\n");
		json.append("    \"forbric:ecosystem\": \"").append(family).append("\"\n");
		json.append("  }\n");
		json.append("}\n");

		Map<String, String> env = new HashMap<>();
		env.put("create", "false");

		URI uri = URI.create("jar:" + remappedJar.toUri());

		try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
			Files.write(fs.getPath("fabric.mod.json"), json.toString().getBytes(StandardCharsets.UTF_8));
			writeAutomaticModuleName(fs, modId, ecosystem);
			pruneDanglingServiceProviders(fs, modId);
		}
	}

	/**
	 * Drop {@code META-INF/services} provider lines whose class is not actually in the jar. Shaded mods
	 * (e.g. spark) ship service files left over from relocated dependencies; a dangling provider makes
	 * automatic-module derivation throw ({@code InvalidModuleDescriptorException: Provider class … not in
	 * JAR}), which would keep the wrapped mod out of Forbric's synthetic GAME module layer — and it would
	 * break {@code ServiceLoader} at runtime anyway.
	 */
	private static void pruneDanglingServiceProviders(FileSystem fs, String modId) throws IOException {
		Path servicesDir = fs.getPath("META-INF", "services");
		if (!Files.isDirectory(servicesDir)) return;
