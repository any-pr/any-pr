/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import net.fabricmc.api.EnvType;

/**
 * {@link UnboundHolderDataInjector}'s output, run: a block a Fabric mod has not registered yet gets its next oxidation
 * stage from vanilla's table — fabric-api's {@code OxidizableBlocksRegistry.registerNextStage} asks exactly that, inside
 * a mod's initializer — where as merged NeoForge's lookup threw {@code Trying to access unbound value}. Once a block is
 * registered, NeoForge's data map still decides for it, and vanilla's table still answers what the data map lacks.
 *
 * <p>The stand-ins keep the merged shapes the edit and its callers key on: {@code Holder.Reference} with an intrusive
 * constructor, a {@code key()} that throws while unbound and the {@code getData} NeoForge patches in (ask the owner when
 * it is a registry, else {@code null}); a registry whose data maps are keyed by registry key; and
 * {@code DataMapHooks.getNextOxidizedStage}, data map first, then vanilla's {@code NEXT_BY_BLOCK}.
 */
@ExecutesInjector(UnboundHolderDataInjector.class)
@ResourceLock("system-properties")
class UnboundHolderDataInjectorExecutionTest {
	private static final String HOOKS = "net.neoforged.neoforge.common.DataMapHooks";
	private static final String BLOCK = "net.minecraft.world.level.block.Block";
	private static final String REGISTRY = "net.minecraft.core.MappedRegistry";

	private static final Map<String, String> STAND_INS = Map.of(
			"net.minecraft.resources.ResourceKey", """
					package net.minecraft.resources;

					public final class ResourceKey<T> {
						private final String id;

						public ResourceKey(String id) {
							this.id = id;
						}

						@Override
						public String toString() {
							return id;
						}
					}
					""",
			"net.neoforged.neoforge.registries.datamaps.DataMapType", """
					package net.neoforged.neoforge.registries.datamaps;

					public final class DataMapType<R, T> {
						private final String id;

						public DataMapType(String id) {
							this.id = id;
						}
					}
					""",
			"net.minecraft.core.HolderOwner", "package net.minecraft.core; public interface HolderOwner<T> { }",
			"net.minecraft.core.HolderLookup", """
					package net.minecraft.core;

					import net.minecraft.resources.ResourceKey;
					import net.neoforged.neoforge.registries.datamaps.DataMapType;

					public interface HolderLookup {
						interface RegistryLookup<T> extends HolderOwner<T> {
							default <A> A getData(DataMapType<T, A> type, ResourceKey<T> key) {
								return null;
							}
						}
					}
					""",
			"net.minecraft.core.Holder", """
					package net.minecraft.core;

					import net.minecraft.resources.ResourceKey;
					import net.neoforged.neoforge.registries.datamaps.DataMapType;

					public interface Holder<T> {
						class Reference<T> implements Holder<T> {
							private final HolderOwner<T> owner;
							private ResourceKey<T> key;
							private final T value;

							private Reference(HolderOwner<T> owner, ResourceKey<T> key, T value) {
								this.owner = owner;
								this.key = key;
								this.value = value;
							}

							public static <T> Reference<T> createIntrusive(HolderOwner<T> owner, T value) {
								return new Reference<>(owner, null, value);
							}

							public ResourceKey<T> key() {
								if (this.key == null) {
									throw new IllegalStateException("Trying to access unbound value '" + this.value + "' from registry " + this.owner);
								}
								return this.key;
							}

							void bindKey(ResourceKey<T> key) {
								this.key = key;
							}

							public <A> A getData(DataMapType<T, A> type) {
								if (this.owner instanceof HolderLookup.RegistryLookup<T> lookup) {
									return lookup.getData(type, this.key());
								}
								return null;
							}
						}
					}
					""",
			REGISTRY, """
					package net.minecraft.core;

					import java.util.HashMap;
					import java.util.Map;
					import net.minecraft.resources.ResourceKey;
					import net.neoforged.neoforge.registries.datamaps.DataMapType;

					/**
					 * Registers by binding the intrusive holder's key; its data maps are keyed by registry key, as NeoForge's.
					 * {@code frozen} is the flag vanilla's validateWrite reads.
					 */
					public class MappedRegistry<T> implements HolderLookup.RegistryLookup<T> {
						private final Map<String, ResourceKey<T>> keys = new HashMap<>();
						private final Map<DataMapType<T, ?>, Map<ResourceKey<T>, Object>> dataMaps = new HashMap<>();
						private boolean frozen;

						public void register(String id, Holder.Reference<T> holder) {
							if (frozen) throw new IllegalStateException("Registry is already frozen");
							ResourceKey<T> key = keys.computeIfAbsent(id, ResourceKey::new);
							holder.bindKey(key);
						}

						public void freeze() {
							frozen = true;
						}

						@Override
						public String toString() {
							return "Registry[test_blocks]";
						}

						public <A> void putData(DataMapType<T, A> type, String id, A value) {
							dataMaps.computeIfAbsent(type, t -> new HashMap<>()).put(keys.computeIfAbsent(id, ResourceKey::new), value);
						}

						@Override
						@SuppressWarnings("unchecked")
						public <A> A getData(DataMapType<T, A> type, ResourceKey<T> key) {
							Map<ResourceKey<T>, Object> values = dataMaps.get(type);
							return values == null ? null : (A) values.get(key);
						}
					}
					""",
			BLOCK, """
					package net.minecraft.world.level.block;

					import net.minecraft.core.Holder;
					import net.minecraft.core.HolderOwner;

					public class Block {
						private final String name;
						private final Holder.Reference<Block> holder;

						public Block(String name, HolderOwner<Block> owner) {
							this.name = name;
							this.holder = Holder.Reference.createIntrusive(owner, this);
						}

						public Holder.Reference<Block> builtInRegistryHolder() {
							return holder;
						}

						@Override
						public String toString() {
							return name;
						}
					}
					""",
			HOOKS, """
					package net.neoforged.neoforge.common;

					import java.util.HashMap;
					import java.util.Map;
					import net.minecraft.world.level.block.Block;
					import net.neoforged.neoforge.registries.datamaps.DataMapType;

					/** NeoForge's shape: its data map first, then vanilla's NEXT_BY_BLOCK (here a plain map). */
					public class DataMapHooks {
						public static final DataMapType<Block, Block> OXIDIZABLES = new DataMapType<>("neoforge:oxidizables");
						public static final Map<Block, Block> NEXT_BY_BLOCK = new HashMap<>();

						public static Block getNextOxidizedStage(Block block) {
							Block next = block.builtInRegistryHolder().getData(OXIDIZABLES);
							return next != null ? next : NEXT_BY_BLOCK.get(block);
						}
					}
					""");

	@AfterEach void reset() {
		System.clearProperty(UnboundHolderDataInjector.PROPERTY);
	}

	/** One game over {@code classes}: a block registry, and blocks made against it. */
	private record Game(ClassLoader loader, Object registry) {
		static Game of(Map<String, byte[]> classes) throws Throwable {
			ClassLoader loader = InjectorExecution.load(classes);
			return new Game(loader, InjectorExecution.construct(loader.loadClass(REGISTRY)));
		}

		Object block(String name) throws Throwable {
			return InjectorExecution.construct(loader.loadClass(BLOCK), name, registry);
		}

		void register(String id, Object block) throws Throwable {
			InjectorExecution.invoke(registry, "register", id, InjectorExecution.invoke(block, "builtInRegistryHolder"));
		}

		@SuppressWarnings("unchecked")
		void vanillaNext(Object block, Object next) throws Throwable {
			((Map<Object, Object>) InjectorExecution.getStatic(loader.loadClass(HOOKS), "NEXT_BY_BLOCK")).put(block, next);
		}

		void dataMapNext(String id, Object next) throws Throwable {
			InjectorExecution.invoke(registry, "putData", InjectorExecution.getStatic(loader.loadClass(HOOKS), "OXIDIZABLES"), id, next);
		}

		Object next(Object block) throws Throwable {
			return InjectorExecution.invokeStatic(loader.loadClass(HOOKS), "getNextOxidizedStage", block);
		}
	}

	private interface Body {
		void run() throws Throwable;
	}

	/** ForbricLog has no log4j binding under test: {@code info} goes to {@code System.out}, {@code warn} to {@code System.err}. */
	private static String capture(Body body) throws Throwable {
		PrintStream out = System.out, err = System.err;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		PrintStream sink = new PrintStream(buffer, true, StandardCharsets.UTF_8);
		System.setOut(sink);
		System.setErr(sink);
		try {
			body.run();
		} finally {
			System.setOut(out);
			System.setErr(err);
		}
		return buffer.toString(StandardCharsets.UTF_8);
	}

	private static Map<String, byte[]> repaired(Map<String, byte[]> original) {
		byte[] bytes = original.get(UnboundHolderDataInjector.OWNER);
		byte[] out = InjectorExecution.transform(new UnboundHolderDataInjector(), UnboundHolderDataInjector.TARGET, bytes, EnvType.SERVER);
		assertNotSame(bytes, out, "the stand-in's getData is the reviewed shape and is edited");
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(UnboundHolderDataInjector.OWNER, out);
		return classes;
	}

	@Test void anUnregisteredBlockGetsItsNextStageFromVanillasTable(@TempDir Path work) throws Throwable {
		Map<String, byte[]> classes = repaired(InjectorExecution.compile(work, STAND_INS));
		Game game = Game.of(classes);
		assertEquals("", InjectorExecution.verify(classes.get(UnboundHolderDataInjector.OWNER), game.loader()));

		// moreladders' onInitialize: registerNextStage(COPPER_LADDER, EXPOSED_COPPER_LADDER) before either is registered.
		Object ladder = game.block("copper_ladder"), exposed = game.block("exposed_copper_ladder");
		game.vanillaNext(ladder, exposed);
		Object[] answers = new Object[2];
		String log = capture(() -> {
			answers[0] = game.next(ladder);
			answers[1] = game.next(exposed);
		});
		assertSame(exposed, answers[0], "an unregistered block's next stage comes from vanilla's table, as on Fabric");
		assertNull(answers[1], "and one vanilla's table does not list has none");
		assertFalse(log.contains("WARN"), "the registry is open: a value about to be registered is the expected case\n" + log);
		assertNull(InjectorExecution.invoke(InjectorExecution.invoke(ladder, "builtInRegistryHolder"), "getData",
				InjectorExecution.getStatic(game.loader().loadClass(HOOKS), "OXIDIZABLES")), "an unbound holder has no data-map entry");

		game.register("moreladders:copper_ladder", ladder);
		assertSame(exposed, game.next(ladder), "registered and missing from the data map: vanilla's table, as before");
	}

	/** NeoForge's semantics once the data map has the block: it decides, over whatever vanilla's table says. */
	@Test void aRegisteredBlockStillFollowsTheDataMap(@TempDir Path work) throws Throwable {
		Game game = Game.of(repaired(InjectorExecution.compile(work, STAND_INS)));
		Object copper = game.block("copper_block"), exposed = game.block("exposed_copper"), other = game.block("weathered_copper");
