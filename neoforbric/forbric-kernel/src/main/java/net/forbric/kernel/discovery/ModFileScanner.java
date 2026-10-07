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

package net.forbric.kernel.discovery;

import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import net.forbric.kernel.util.ForbricLog;

/**
 * Builds a {@code ModFileScanData} for a NeoForge mod jar — the annotation index FML hands to mods, and the only
 * way several of them find their own extensions.
 *
 * <p>The kernel constructs {@code @Mod} classes from its own targeted scan ({@link ModAnnotationScanner}) and never
 * needed a full index, so {@code IModFile.getScanResult()} returned an EMPTY one. That is invisible right up until
 * a mod asks: JEI's {@code ForgePluginFinder} walks {@code ModList.getAllScanData()} for {@code @JeiPlugin} and,
 * finding none, threw {@code IllegalArgumentException: plugins must not be empty} out of its own {@code @Mod}
 * constructor. Jade and Sophisticated Core discover their plugins the same way, and Sodium finds third-party config
 * entry points through it too — those fail silently, which is worse.
 *
 * <p>Scanning is per jar and LAZY: nothing walks a jar until something actually calls {@code getScanResult()}, so
 * an instance whose mods never ask pays nothing. Class bodies are skipped ({@code SKIP_CODE | SKIP_DEBUG |
 * SKIP_FRAMES}) — only the annotation tables are read.
 *
 * <p><b>Both visible and INVISIBLE annotations</b>, on classes, methods and fields. The index is built from
 * bytecode, not reflection, so {@code CLASS}-retention annotations are just as readable — and they are the norm
 * here: {@code @JeiPlugin} is {@code RuntimeInvisible}, so a visible-only scan finds zero JEI plugins and JEI dies
 * with "plugins must not be empty" exactly as it did with no index at all.
 */
public final class ModFileScanner {
	private static final String GAME_SIDE = "net.forbric.kernel.runtime.KernelScanData";
	public static final String SEEDED_INDEX_PROPERTY = "forbric.seededScanData";
	public static final String REAL_PATH_KEY_PROPERTY = "forbric.seededScanData.realPath";

	private ModFileScanner() {
	}

	/**
	 * One annotation occurrence, in the shape {@code ModFileScanData.AnnotationData} takes. Package-private so the
	 * tests can assert the member-name SHAPES without a live NeoForge SPI on the classpath — the shapes are the whole
	 * contract here, and getting one wrong is invisible until a mod's plugin system quietly finds nothing.
	 */
	public record Found(String annotationDesc, ElementType target, String ownerInternalName, String memberName,
			Map<String, Object> values) {
	}

	/**
	 * One class triple, in the shape {@code ModFileScanData.ClassData} takes.
	 *
	 * <p>This was an {@code Object[]} of length three, read back positionally as {@code c[0]}/{@code c[1]}/
	 * {@code c[2]} and handed straight to a reflectively-resolved constructor. Two unchecked things in a row: the
	 * array said nothing about what belonged in each slot, and the constructor lookup said nothing about whether
	 * it still took them in that order. Swapping {@code name} and {@code parent} would have compiled, run, and
	 * produced an index in which every class claims to extend itself — which no mod would report, they would just
	 * find nothing.
	 */
	public record ClassEntry(Type name, Type parent, Set<Type> interfaces) {
	}

	/**
	 * An enum-valued annotation member, still unwrapped.
	 *
	 * <p>It cannot be materialised here, because {@code ModAnnotation.EnumHolder} is a game-side type: it stores the
	 * descriptor and the constant's name. So the ASM pass records the two strings and the game-side builder wraps
	 * them.
	 *
	 * <p>This was a bare {@code String} — "the constant's simple name, which is what consumers compare against",
	 * said a comment that was simply wrong about FML. What it cost, the first time an index was non-empty:
	 * SuperMartijn642's Core Lib casts {@code annotationData().get("registry")} to the enum holder without
	 * checking, so every {@code @RegistryEntryAcceptor} in the pack threw a {@code ClassCastException} and Core Lib
	 * turned that into "Failed to register @RegistryEntryAcceptor annotation target" out of its construct listener.
	 */
	public record EnumValue(String descriptor, String value) {
	}

	/**
	 * Replaces every {@link EnumValue} in an annotation's value map with what {@code wrap} makes of it, walking
	 * arrays and nested annotations. Called by the game-side builder, which passes the ecosystem's own constructor —
	 * the walk stays here so the shape cannot drift from the collector that produces it.
	 */
	public static Map<String, Object> wrapEnums(Map<String, Object> values,
			java.util.function.BiFunction<String, String, Object> wrap) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (Map.Entry<String, Object> e : values.entrySet()) out.put(e.getKey(), wrapValue(e.getValue(), wrap));
		return out;
	}

	@SuppressWarnings("unchecked")
	private static Object wrapValue(Object value, java.util.function.BiFunction<String, String, Object> wrap) {
		if (value instanceof EnumValue enumValue) return wrap.apply(enumValue.descriptor(), enumValue.value());
		if (value instanceof List<?> list) {
			List<Object> items = new ArrayList<>(list.size());
			for (Object item : list) items.add(wrapValue(item, wrap));
			return items;
		}
		if (value instanceof Map<?, ?> nested) return wrapEnums((Map<String, Object>) nested, wrap);
		return value;
	}

	/**
	 * Scans {@code jar} and returns a populated {@code ModFileScanData}, or null if one cannot be built.
	 *
	 * <p>Two halves with a clean line between them. The ASM pass ({@link #collect}) names no game type and stays
	 * here, on the boot side, where the rest of the kernel's bytecode machinery lives. Turning its output into the
	 * NeoForge SPI's own records is the only part that needs game types, and it now lives on the GAME side
	 * ({@code net.forbric.kernel.runtime.KernelScanData}) where a compiler checks it.
	 *
	 * <p>That materialisation used to be three {@code Class.forName} calls, two constructor lookups by exact
	 * parameter list, and two {@code getMethod} calls — six independent strings holding up an index whose only
	 * failure mode is that mods quietly find nothing in it.
	 *
	 * @param gameLoader the loader holding the NeoForge SPI
	 */
	public static Object scan(Path jar, ClassLoader gameLoader) {
		return materialise(jar, gameLoader, GAME_SIDE, "NeoForge");
	}

	/**
	 * {@code -Dforbric.seededScanData=off} puts back both halves of the shared NeoForge index: the {@code ModFile}s
	 * in the seeded NeoForge {@code LoadingModList} go back to having no scan at all (so {@code getScanResult()}
	 * throws FML's own "Scanning of this mod file has not started yet."), and each {@code KernelModFile} goes back
	 * to scanning its jar for itself ({@link #scanShared} becomes plain {@link #scan}).
	 */
	public static boolean seededIndexEnabled() {
		return !"off".equalsIgnoreCase(System.getProperty(SEEDED_INDEX_PROPERTY, "on"));
	}

	/**
	 * NeoForge indexes already built, per game loader and then per jar. Per loader because an index is an
	 * instance of THAT loader's {@code ModFileScanData}, and handed to a reader under another loader it is a
	 * ClassCastException; in a real boot there is exactly one key.
	 */
	private static final Map<ClassLoader, Map<Path, Object>> SHARED_NEO_INDEX = new java.util.HashMap<>();

	/**
	 * {@link #scan}, built at most once per jar and handed to every reader of that jar — or null, uncached, when
	 * the index cannot be built, so each caller keeps its own fallback.
	 *
	 * <p>There are two readers and natively they are one object. FML builds ONE {@code ModFile} per jar and
	 * {@code ModList} is made out of {@code LoadingModList}, so {@code ModList.getAllScanData()} and a walk over
	 * {@code LoadingModList.getModFiles()} read the very same {@code ModFileScanData}. The kernel builds two
	 * different file objects for those two lists (its own {@code KernelModFile} and the seeded concrete
	 * {@code ModFile}); without this they would scan the same jar twice and hand out two indexes, and whatever is
	 * added to one of them (every collection in a {@code ModFileScanData} is mutable) would be missing from the
	 * other.
	 *
	 * <p>What made the second reader matter: RollingGate's constructor walks {@code LoadingModList.getModFiles()}
	 * for its rule containers (Server++'s are found by the same walk), so an instance with RollingGate asks for
	 * the index of every NeoForge jar from inside that one constructor — and then JEI, Jade and Sophisticated Core
	 * ask {@code ModList} for the same jars again.
	 */
	public static Object scanShared(Path jar, ClassLoader gameLoader) {
		if (!seededIndexEnabled()) return scan(jar, gameLoader);
		Path key = cacheKey(jar);
		synchronized (SHARED_NEO_INDEX) {
			Map<Path, Object> byJar = SHARED_NEO_INDEX.get(gameLoader);
			Object known = byJar == null ? null : byJar.get(key);
			if (known != null) return known;
		}
		// Built outside the lock: a slow jar must not hold up the others. Two threads racing on one jar both scan
		// it, and the loser takes the winner's object, which is what keeps the answer a single object.
		Object built = scan(jar, gameLoader);
		if (built == null) return null;
		synchronized (SHARED_NEO_INDEX) {
			Object first = SHARED_NEO_INDEX.computeIfAbsent(gameLoader, loader -> new java.util.HashMap<>())
					.putIfAbsent(key, built);
			return first != null ? first : built;
		}
	}

	/**
	 * The one spelling of a jar both readers share. The real path, so a symlink, macOS's {@code /tmp} for
	 * {@code /private/tmp} or a Windows 8.3 short name cannot make one jar two keys, two scans and two indexes, the
	 * split {@link #scanShared} exists to prevent. The normalised absolute path when the file cannot be resolved.
	 *
	 * <p>{@code -Dforbric.seededScanData.realPath=off} keys on the normalised absolute path alone, as before.
	 */
	static Path cacheKey(Path jar) {
		Path spelled = jar.toAbsolutePath().normalize();
		if ("off".equalsIgnoreCase(System.getProperty(REAL_PATH_KEY_PROPERTY, "on"))) return spelled;
		try {
			return jar.toRealPath();
		} catch (java.io.IOException | SecurityException unresolvable) {
			return spelled;
		}
	}

	private static Object materialise(Path jar, ClassLoader gameLoader, String gameSide, String ecosystem) {
		try {
			List<Found> found = new ArrayList<>();
			List<ClassEntry> classes = new ArrayList<>();
			collect(jar, found, classes);

			Object scanData = Class.forName(gameSide, true, gameLoader)
					.getMethod("build", List.class, List.class)
					.invoke(null, found, classes);

			ForbricLog.debug("[Forbric/Scan] %s (%s): %d annotation(s) over %d class(es)", jar.getFileName(),
					ecosystem, found.size(), classes.size());
			return scanData;
		} catch (Throwable t) {
			ForbricLog.debug("[Forbric/Scan] could not scan %s for %s: %s", jar.getFileName(), ecosystem,
					String.valueOf(t));
			return null;
		}
	}

	/**
	 * The ASM pass on its own — every annotation occurrence and every class triple in {@code jar}. Package-private:
	 * this half needs no game classes, so it is the half the tests can reach.
	 */
	public static void collect(Path jar, List<Found> found, List<ClassEntry> classes) throws Exception {
		try (JarFile zip = new JarFile(jar.toFile())) {
			for (var entries = zip.entries(); entries.hasMoreElements();) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (entry.isDirectory() || !name.endsWith(".class")) continue;
				// module-info/package-info carry no members anything looks up, and module-info is not a normal class.
				if (name.endsWith("module-info.class") || name.endsWith("package-info.class")) continue;

				try (InputStream in = zip.getInputStream(entry)) {
					new ClassReader(in).accept(new Collector(found, classes),
							ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				} catch (Throwable perClass) {
					// One unreadable class (a newer class-file version, a shaded oddity) must not cost the jar its
					// whole index — the mods that read it would then silently find nothing.
					ForbricLog.debug("[Forbric/Scan] skipping %s in %s: %s", name, jar.getFileName(),
							String.valueOf(perClass));
				}
			}
		}
	}

	/** Records every annotation, visible or not, on the class and on each member, plus the class/super/interfaces triple. */
	private static final class Collector extends ClassVisitor {
		private final List<Found> found;
		private final List<ClassEntry> classes;
		private String internalName;

		Collector(List<Found> found, List<ClassEntry> classes) {
			super(Opcodes.ASM9);
			this.found = found;
			this.classes = classes;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName,
				String[] interfaces) {
			this.internalName = name;
			Set<Type> parents = new LinkedHashSet<>();
			if (interfaces != null) {
				for (String i : interfaces) parents.add(Type.getObjectType(i));
			}
			// A null superclass stays null, the way FML records it. Only java/lang/Object itself has none, but a
			// consumer comparing parent() against null has to get the same answer it would from genuine FML.
			classes.add(new ClassEntry(
					Type.getObjectType(name),
					superName == null ? null : Type.getObjectType(superName),
					parents));
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			// FML records a TYPE annotation's member as the DOTTED binary name, and its consumers take that
			// literally: JEI's ForgePluginFinder reads only annotationType() and memberName(), then calls
			// Class.forName(memberName) with no normalisation at all. Handing it the slashed internal name found
			// every @JeiPlugin in the pack and then lost all nine to ClassNotFoundException, leaving JEI to throw
			// "plugins must not be empty" out of its own @Mod constructor. Jade and JourneyMap discover their
