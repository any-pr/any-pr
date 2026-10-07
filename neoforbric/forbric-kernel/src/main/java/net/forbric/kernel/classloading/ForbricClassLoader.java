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

package net.forbric.kernel.classloading;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import net.forbric.kernel.util.ForbricLog;

/**
 * The kernel's single sovereign transforming class loader — the one and only loader that defines the game +
 * ecosystem classes, applying the unified transform pipeline as each class is defined.
 *
 * <p>This replaces Fabric's Knot (and Forge's ModLauncher/securemodules) with one flat, JPMS-free loader. It is
 * child-first for the jars it owns (the merged base + the Forge/NeoForge runtime carriers + the kernel's game-side
 * runtime jar + mod jars) and parent-first for everything shared with the boot side (ASM, Mixin, logging, the
 * kernel boot classes) — see {@link DelegationPolicy}.
 *
 * <p>The transform hook is injected by the boot orchestrator: {@code (binaryName, classBytes) -> newBytes}. It
 * runs the {@code TransformChain} (Access, merged-base compat, the kernel redirectors) and, last, Mixin. A
 * {@code null}/identity return means "unchanged".
 */
public final class ForbricClassLoader extends URLClassLoader {
	static {
		ClassLoader.registerAsParallelCapable();
	}

	private final ClassLoader parent;
	private volatile ClassLoader fallbackClassLoader;
	private final DefinedClassEvidence definitionEvidence = new DefinedClassEvidence();

	/** One {@link ProtectionDomain} per owned jar, keyed by the jar URL's spelling. See {@link #domainFor}. */
	private final Map<String, ProtectionDomain> domains = new ConcurrentHashMap<>();

	private volatile BiFunction<String, byte[], byte[]> transformer = (n, b) -> b;
	private volatile BiFunction<String, byte[], byte[]> mixinTransformer = (n, b) -> b;

	public ForbricClassLoader(URL[] ownedJars, ClassLoader parent) {
		super("forbric", ownedJars, parent);
		this.parent = parent;
	}

	/**
	 * Adds a jar to the set this loader owns, at runtime, after boot.
	 *
	 * <p>{@code URLClassLoader} declares this {@code protected}, and mods that unpack their real payload during
	 * {@code preLaunch} look for it with {@code getDeclaredMethod}, which does not search superclasses — so a
	 * protected inherited method reads to them as absent. Essential's stage-2 loader probes for exactly this
	 * signature and, not finding it, gives up with "Failed to add Essential jar to parent ClassLoader".
	 *
	 * <p>Overriding it public is also the honest contract: a jar added here is OWNED, so its classes go through the
	 * whole pipeline (access tweakers, the compat chain, then Mixin) like any other mod's — which is what a mod
	 * extending the classpath at runtime expects, and what Fabric's own {@code addToClassPath} gives it.
	 */
	@Override
	public void addURL(URL url) {
		super.addURL(url);
	}

	/**
	 * Attaches a mod's extracted runtime archives to the transforming loader. Universal mods such as
	 * SimpleGUI expose their payload through a URLClassLoader and reflectively request this interface.
	 * Owning those URLs keeps game types, access transforms and Mixin on the same loader, rather than
	 * defining a second copy through a child that delegates back here. Like addURL, this does not unload
	 * previously attached archives; a null value only clears the fallback marker used by runtime bridges.
	 */
	public synchronized void setFallbackClassLoader(ClassLoader fallback) {
		if (fallback == null) { fallbackClassLoader = null; return; }
		if (fallback == this || !(fallback instanceof URLClassLoader urls)) {
			throw new IllegalArgumentException("A runtime fallback must expose its archives through URLClassLoader");
		}
		for (URL url : urls.getURLs()) addURL(url);
		fallbackClassLoader = fallback;
		preMixin.clear();
	}

	/** Installs the pre-mixin transform chain (Access, compat, the kernel redirectors). Call once, before any load. */
	public void setTransformer(BiFunction<String, byte[], byte[]> transformer) {
		this.transformer = transformer == null ? (n, b) -> b : transformer;
		// Anything remembered before the chain existed was remembered UNTRANSFORMED. Mixin would then inspect
		// bytes that do not match the ones this loader defines, which is the one way this cache could be wrong.
		preMixin.clear();
		chainInstalled = true;
	}

	/**
	 * Transformed bytes Mixin has already been shown, kept so it need not be rebuilt.
	 *
	 * <p>{@link #getPreMixinClassBytes} re-read the jar and re-ran the WHOLE transform chain on every call, and
	 * Mixin asks repeatedly for the same classes — each anchor it resolves walks its target's superclass chain,
	 * and the chains of the game's own types are asked about again and again.
	 *
	 * <p>Soft references rather than a plain map: these are whole class files and the set Mixin asks about is not
	 * bounded by anything the kernel controls, so the JVM is left free to drop them under memory pressure. A drop
	 * costs one rebuild, which is what every call used to cost.
	 */
	private final java.util.Map<String, java.lang.ref.SoftReference<byte[]>> preMixin = new ConcurrentHashMap<>();

	/** False until the transform chain is installed; see {@link #setTransformer}. */
	private volatile boolean chainInstalled;

	private byte[] rememberedPreMixin(String name) {
		java.lang.ref.SoftReference<byte[]> held = preMixin.get(name);
		return held == null ? null : held.get();
	}

	private void rememberPreMixin(String name, byte[] bytes) {
		// Never before the chain is installed: the answer would be the untransformed class, and it would then be
		// handed out for the rest of the run.
		if (chainInstalled) preMixin.put(name, new java.lang.ref.SoftReference<>(bytes));
	}

	/**
	 * Jars that were SUPERSEDED by another copy of the same mod, consulted ONLY when a class is in no owned jar.
	 *
	 * <p>Cross-jar arbitration keeps one jar per mod id and drops the other, which is required: two builds of one
	 * mod share most class NAMES but not their bytes (measured: 90 of Jade's 436 shared classes differ, 29 of
	 * lithostitched's 346), so putting both on the classpath would mix two builds under first-URL-wins. What that
	 * costs is the loser's handful of platform-only classes — 40 across the nine superseded jars of the merged pack,
	 * 0 to 17 each.
	 *
	 * <p>Nothing in that pack referenced any of them, but a mod that IS built against the other side's platform
	 * class would hit a bare {@code NoClassDefFoundError} with nothing pointing at the cause. Serving them as a
	 * last resort closes that: because this is reached only after {@link #findResource} misses, it cannot shadow the
	 * winner — the disjointness is structural rather than something to compute and trust.
	 *
	 * <p><b>Classes only, never resources.</b> The superseded jar's {@code *.mixins.json} and {@code assets/} must
	 * stay unreachable — not applying them twice is the whole point of suppressing it.
	 *
	 * <p><b>This fixes linkage, not initialisation.</b> A platform class whose own side never ran its {@code @Mod} /
	 * entrypoint may still fail on state that was never set up. That case needs the mod pinned to the other
	 * ecosystem instead, which is what the rescue log line tells the user to do.
	 */
	public void setRescueJars(List<URL> jars) {
		rescue = (jars == null || jars.isEmpty()) ? null : new URLClassLoader(jars.toArray(new URL[0]), null);
	}

	private volatile URLClassLoader rescue;
	private static final Set<String> RESCUED = ConcurrentHashMap.newKeySet();

	/** A class the owned jars do not have, from a superseded jar. Null when there is no rescue set or no such class. */
	private URL rescueResource(String path) {
		URLClassLoader superseded = rescue;
		return superseded == null ? null : superseded.findResource(path);
	}

	/**
	 * Offers a class synthesized by a transformer (class-tweaker enum extension) for definition on demand. The
	 * bytes are used verbatim; the class is defined the first time something loads it.
	 *
	 * @param internalName the ASM internal name ({@code a/b/C})
	 */
	public void putGeneratedClass(String internalName, byte[] bytes) {
		String binary = internalName.replace('/', '.');
		generatedClasses.put(binary, bytes);
		// Whatever Mixin was shown for this name before is no longer what the loader will define.
		preMixin.remove(binary);
	}

	/**
	 * Installs the Mixin weaver, which runs strictly AFTER {@link #setTransformer the chain} — the last stage of
	 * the pipeline, as Mixin requires.
	 *
	 * <p>It is invoked with {@code null} bytes for a class not present in any owned jar: that is Mixin's
	 * class-GENERATION path (e.g. {@code org.spongepowered.asm.synthetic.*} argument classes), which must return
	 * bytes or {@code null}. It must not be given already-woven bytes, or it would weave its own output.
	 */
	public void setMixinTransformer(BiFunction<String, byte[], byte[]> mixinTransformer) {
		this.mixinTransformer = mixinTransformer == null ? (n, b) -> b : mixinTransformer;
	}

	/**
	 * The bytes Mixin's bytecode provider must see for {@code name}: read from the owned jars and put through the
	 * pre-mixin chain, but NOT woven. Falls back to the parent's resources for library classes Mixin inspects
	 * (superclasses, interfaces), which are never transformed. {@code null} if the class has no bytes anywhere.
	 */
	public byte[] getPreMixinClassBytes(String requested) {
		// Mixin asks by binary (dotted) name; a mod using the bytecode provider directly may ask by INTERNAL name
		// (fabric-item-api's tooltip-order scrape passes Type.getInternalName(ItemStack.class)). The chain's
		// transformers compare binary names, so a slashed name would silently skip every repair and the caller
		// would be handed bytes the game never runs — and the cache would hold two entries for one class.
		String name = requested.replace('/', '.');
		byte[] remembered = rememberedPreMixin(name);
		if (remembered != null) return remembered;

		String path = name.replace('.', '/') + ".class";
		URL resource = findResource(path);
		if (resource == null) {
			// The same order tryDefineGameClass defines in: offered bytes, verbatim, before a superseded jar. Without
			// this Mixin found NO class for an offered name (a class-tweaker enum extension, the Mod Menu API
			// stand-in) while the loader defined one, so a mixin whose target's hierarchy runs through it could not
			// be resolved here although it resolves on the instance where a jar carries the same class.
			byte[] generated = generatedClasses.get(name);
			if (generated != null) return generated;
		}
		// Same last-resort as tryDefineGameClass, or Mixin would inspect different bytes than the ones defined.
		if (resource == null) resource = rescueResource(path);

		if (resource != null) {
			byte[] raw = read(resource);
			if (raw == null) return null;

			byte[] transformed = transformer.apply(name, raw);
			byte[] result = transformed == null ? raw : transformed;
			rememberPreMixin(name, result);
			return result;
		}

		try (InputStream in = parent.getResourceAsStream(path)) {
			return in == null ? null : in.readAllBytes();
		} catch (IOException e) {
			return null;
		}
	}

	/** Whether this loader has already defined {@code name} (Mixin's {@code IClassTracker}). */
	public boolean isClassLoadedByName(String name) {
		synchronized (getClassLoadingLock(name)) {
			return findLoadedClass(name) != null;
		}
	}

	/** Resource lookup for Mixin config JSONs: this loader's own jars first, then the parent. */
	public InputStream getGameResourceAsStream(String name) {
		URL url = findResource(name);

		if (url != null) {
			try {
				return url.openStream();
			} catch (IOException e) {
				return null;
			}
		}

		return parent.getResourceAsStream(name);
	}

	private static byte[] read(URL resource) {
		try (InputStream in = resource.openStream()) {
			return in.readAllBytes();
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Defines a kernel-generated class in THIS loader, so generated glue (e.g. the container-factory's
	 * {@code ModContainer} subclass) shares the game's class identity and can extend game/ecosystem types.
	 * The bytes are used verbatim (no transform). Returns the already-defined class if present.
	 */
	public Class<?> defineRuntimeClass(String binaryName, byte[] bytes) {
		synchronized (getClassLoadingLock(binaryName)) {
			Class<?> existing = findLoadedClass(binaryName);
			if (existing != null) return existing;
			definePackageIfNeeded(binaryName, null); // generated class, no owning jar
			return define(binaryName, bytes, null);
		}
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> c = findLoadedClass(name);
			if (c == null) {
				// -Dforbric.fabricImpl=off: the shipped Fabric Loader internals are not there, as before they were.
				if (FabricLoaderInternals.withheld(name)) {
					throw new ClassNotFoundException(name + " (withheld: -D" + FabricLoaderInternals.SWITCH + "=off)");
				}
				if (DelegationPolicy.alwaysParent(name)) {
					c = parent.loadClass(name);
				} else if (DelegationPolicy.alwaysGame(name)) {
					c = defineGameClass(name); // must be here; if bytes missing this throws (a real error)
				} else {
					// Child-first for owned jars, else parent. Catches game/mod classes without a package list,
