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

package net.forbric.kernel.fabric;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.entrypoint.EntrypointStorage;

import net.forbric.kernel.classloading.FabricLoaderInternals;
import net.forbric.kernel.fabric.KernelModMetadata.EntrypointDecl;
import net.forbric.kernel.util.ForbricLog;

/**
 * The sovereign kernel's {@link FabricLoader} — the singleton every Fabric mod reaches through
 * {@code FabricLoader.getInstance()}.
 *
 * <p>This replaces Fabric Loader's {@code FabricLoaderImpl} outright. The kernel owns discovery, classloading
 * and the lifecycle, so this type is a view over kernel state rather than a loader in its own right: the boot
 * orchestrator {@linkplain #register registers} the discovered mods, then {@linkplain #freeze freezes} the
 * entrypoint index before any mod code runs.
 *
 * <p><b>Class identity.</b> This class and the whole {@code net.fabricmc.loader.api} surface are parent-loaded
 * (see {@code DelegationPolicy}), while mod classes are defined by the transforming {@code ForbricClassLoader}.
 * That is what makes a game-side mod's {@code implements ModInitializer} resolve to the same interface this
 * boot-side code casts to. Entrypoint classes are therefore resolved through {@link #gameLoader}, never
 * through this class's own loader.
 */
public final class KernelFabricLoader implements FabricLoader {
	private static volatile KernelFabricLoader instance;

	/**
	 * The launch arguments a sanitised read must not contain, with the value that follows each.
	 *
	 * <p>The same four Fabric removes. {@code --accessToken} is the one that matters: it is a live session
	 * credential, and a mod asks for the SANITISED arguments precisely when it is about to write them somewhere
	 * that leaves the machine.
	 */
	private static final java.util.Set<String> SENSITIVE_ARGUMENTS =
			java.util.Set.of("--accessToken", "--username", "--uuid", "--xuid");

	/**
	 * {@code -Dforbric.entrypointResolveFailure=warn}: an entrypoint whose class cannot be loaded is skipped with a
	 * WARN, as before, instead of failing its mod (lifecycle keys) or recording a finding (other keys).
	 */
	public static final String RESOLVE_FAILURE_PROPERTY = "forbric.entrypointResolveFailure";

	/** The keys the kernel itself drives through the lifecycle; a mod whose entry among them cannot load did not start. */
	private static final java.util.Set<String> LIFECYCLE_KEYS = java.util.Set.of("main", "client", "server", "preLaunch");

	private final EnvType envType;
	private final Path gameDir;
	private final Path configDir;
	private final String[] launchArguments;
	private final String rawGameVersion;

	private final ObjectShare objectShare = new KernelObjectShare();
	private final MappingResolver mappingResolver = new KernelMappingResolver();

	private final List<ModContainer> mods = new ArrayList<>();
	private final Map<String, ModContainer> modsById = new LinkedHashMap<>();
	private final Map<String, List<Entrypoint>> entrypointsByKey = new LinkedHashMap<>();

	private volatile ClassLoader gameLoader;
	private volatile Object gameInstance;
	private volatile boolean frozen;

	/**
	 * The map behind Fabric Loader's {@code EntrypointStorage.entryMap} (see {@link #fabricEntryMap()}). One per
	 * process, like the {@code FabricLoaderImpl.INSTANCE} that exposes it; filled from whichever loader froze last.
	 */
	private static final Map<String, List<EntrypointStorage.Entry>> FABRIC_ENTRY_MAP = new LinkedHashMap<>();
	/** Whether a mod has reached for the storage at all; until then the kernel never builds it. */
	private static boolean fabricStorageLive;
	/** The loader {@link #FABRIC_ENTRY_MAP} was last filled from; only that loader may read it back. */
	private static KernelFabricLoader fabricStorageSeededBy;

	private KernelFabricLoader(EnvType envType, Path gameDir, Path configDir, String[] launchArguments,
			String rawGameVersion) {
		this.envType = envType;
		// Absolute, and the config directory made. A mod reads getGameDir()/getConfigDir() to decide where to put
		// its own files, and both answers used to be whatever the launcher happened to pass: a RELATIVE path
		// resolves against the process's working directory, which is not the game directory for every launcher,
		// so a mod wrote its files somewhere else entirely. And a mod writing straight into the config directory
		// got a NoSuchFileException on a first run, because nothing had created it.
		this.gameDir = absolute(gameDir);
		this.configDir = created(absolute(configDir));
		this.launchArguments = launchArguments == null ? new String[0] : launchArguments.clone();
		this.rawGameVersion = rawGameVersion;
	}

	/** A path a mod can resolve against without knowing the process's working directory. Null stays null. */
	static Path absolute(Path path) {
		try {
			return path == null ? null : path.toAbsolutePath().normalize();
		} catch (Throwable unresolvable) {
			return path;
		}
	}

	/**
	 * The config directory, created if it is not there.
	 *
	 * <p>Fabric creates it before any mod can ask, and a mod that writes its config on first run has no reason to
	 * create it itself. Failure to create is not fatal here: the mod's own write will fail and say so with its
	 * own name on it, which is better than failing the boot for every other mod.
	 */
	static Path created(Path dir) {
		try {
			if (dir != null) java.nio.file.Files.createDirectories(dir);
		} catch (Throwable notCreated) {
			ForbricLog.debug("[Forbric/Fabric] could not create the config directory %s: %s", dir,
					String.valueOf(notCreated));
		}
		return dir;
	}

	/** The live instance, or {@code null} if the kernel has not created it yet ({@code FabricLoader.getInstance()}). */
	public static KernelFabricLoader getInstanceOrNull() {
		return instance;
	}

	/** Creates the process-wide instance. Called once by the boot orchestrator, before any mod class loads. */
	public static synchronized KernelFabricLoader create(EnvType envType, Path gameDir, Path configDir,
			String[] launchArguments, String rawGameVersion) {
		if (instance != null) throw new IllegalStateException("KernelFabricLoader already created");

		KernelLanguageAdapters.reset();
		instance = new KernelFabricLoader(envType, gameDir, configDir, launchArguments, rawGameVersion);
		return instance;
	}

	/** The transforming loader that defines mod + game classes; entrypoints resolve through it. */
	public void setGameLoader(ClassLoader loader) {
		this.gameLoader = loader;
		KernelLanguageAdapters.bindGameLoader(loader);
	}

	/** Publishes the {@code MinecraftServer} / {@code Minecraft} object for {@link #getGameInstance()}. */
	public void setGameInstance(Object gameInstance) {
		this.gameInstance = gameInstance;
	}

	/** Adds a discovered mod. Its id and every {@code provides} alias become resolvable. */
	public synchronized void register(KernelModContainer container) {
		if (frozen) throw new IllegalStateException("mods registered after freeze");

		KernelModMetadata metadata = container.getMetadata();
		ModContainer existing = modsById.get(metadata.getId());

		if (existing != null) {
			ForbricLog.warn("[Forbric/Fabric] duplicate mod id '%s' (%s and %s) — keeping the first",
					metadata.getId(), existing, container);
			return;
		}

		mods.add(container);
		modsById.put(metadata.getId(), container);

		for (String alias : metadata.getProvides()) {
			modsById.putIfAbsent(alias, container);
		}

		// Registered at discovery, not at first use: an adapter is declared by ONE mod and named by others, so it
		// has to be known before any entrypoint is constructed regardless of discovery order.
		KernelLanguageAdapters.declare(metadata.getId(), metadata.getLanguageAdapters());

		for (Map.Entry<String, List<EntrypointDecl>> entry : metadata.getEntrypoints().entrySet()) {
			List<Entrypoint> sink = entrypointsByKey.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());

			for (EntrypointDecl decl : entry.getValue()) {
				sink.add(new Entrypoint(entry.getKey(), container, decl));
			}
		}
	}

	/**
	 * Puts the registered mods in {@code order}, and every entrypoint key's list with them.
	 *
	 * <p>Fabric Loader fills its entrypoint storage mod by mod, walking its mod list, so each key hands its
	 * entrypoints back grouped by mod, in mod order, and in declaration order within one mod. Re-sorting each key by
	 * the new position of the mod that declared the entry keeps that shape. The sort is stable, so a mod's own entries
	 * keep the order it declared them in.
	 *
	 * <p>Only the order changes. The id and alias lookups were settled at registration, so which container answers
	 * for an id (the first registered, see {@link #register}) stays the same.
	 *
	 * @param order exactly the mods registered so far, each once, compared by identity
	 * @throws IllegalStateException if the loader is already frozen, because entrypoints may have run in the old order
	 * @throws IllegalArgumentException if {@code order} is not a permutation of the registered mods. Nothing is changed.
	 */
	public synchronized void reorder(List<? extends ModContainer> order) {
		if (frozen) throw new IllegalStateException("mods reordered after freeze");

		Map<ModContainer, Integer> rank = new IdentityHashMap<>();
		for (ModContainer mod : order) {
			if (rank.putIfAbsent(mod, rank.size()) != null) {
				throw new IllegalArgumentException("mod listed twice in the new order: " + mod);
			}
		}
		if (rank.size() != mods.size()) {
			throw new IllegalArgumentException("the new order lists " + rank.size() + " mod(s), " + mods.size()
					+ " are registered");
		}
		for (ModContainer mod : mods) {
			if (!rank.containsKey(mod)) throw new IllegalArgumentException("the new order leaves out " + mod);
		}

		mods.clear();
		mods.addAll(order);
		for (List<Entrypoint> entries : entrypointsByKey.values()) {
			entries.sort(java.util.Comparator.comparingInt(
					entry -> rank.getOrDefault(entry.provider(), Integer.MAX_VALUE)));
		}
	}

	/**
	 * Seals the mod set. Everything after this point is read-only, so entrypoint lookup needs no lock — with one
	 * exception, {@link #adoptFabricStorage}, which runs on the boot thread between the entrypoint phases.
	 */
	public synchronized void freeze() {
		frozen = true;
		ForbricLog.info("[Forbric/Fabric] FabricLoader ready — %d mod(s), entrypoint keys %s",
				mods.size(), entrypointsByKey.keySet());
		synchronized (FABRIC_ENTRY_MAP) {
			if (fabricStorageLive) seedFabricStorage();
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Fabric Loader's internal entrypoint storage
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * The live map {@code EntrypointStorage.entryMap} is, for the mods that edit Fabric Loader's entrypoint index by
	 * reflection instead of declaring entrypoints (see {@code FabricLoaderImpl}).
	 *
	 * <p>It holds one entry per entrypoint this loader holds, per key, in this loader's order, in mutable lists. The
	 * kernel keeps its own index — this map is a copy handed out for editing, and {@link #adoptFabricStorage} is the
	 * only way an edit reaches the kernel. Built on first request, and never before: a pack with no mod that reaches
	 * for it never has one.
	 */
	public static Map<String, List<EntrypointStorage.Entry>> fabricEntryMap() {
		synchronized (FABRIC_ENTRY_MAP) {
			fabricStorageLive = true;
			KernelFabricLoader current = instance;
			if (current != null && current.frozen && fabricStorageSeededBy != current) current.seedFabricStorage();
		}
		return FABRIC_ENTRY_MAP;
	}

	/** Refills {@link #FABRIC_ENTRY_MAP} from this loader. Caller holds its lock. */
	private void seedFabricStorage() {
		FABRIC_ENTRY_MAP.clear();
		for (Map.Entry<String, List<Entrypoint>> key : entrypointsByKey.entrySet()) {
			List<EntrypointStorage.Entry> entries = new ArrayList<>(key.getValue().size());
			for (Entrypoint entry : key.getValue()) entries.add(entry.storageEntry());
			FABRIC_ENTRY_MAP.put(key.getKey(), entries);
		}
		fabricStorageSeededBy = this;
	}

