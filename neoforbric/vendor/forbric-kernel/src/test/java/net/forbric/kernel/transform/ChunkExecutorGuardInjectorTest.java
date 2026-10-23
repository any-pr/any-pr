/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.api.EnvType;

/**
 * {@link ChunkExecutorGuardInjector}'s synthesized {@code execute}, run: a stopped server's chunk executor refuses work,
 * a live one still queues it.
 *
 * <p>The stand-ins are the two classes the emitted body links against and nothing else: an event loop whose
 * {@code execute} queues, and a chunk cache whose executor reports the cache's own thread. The guard it calls is the
 * kernel's real {@code KernelChunkExecutorGuard}, loaded by the test's loader, so the refusal seen here is the one a
 * player would get.
 */
@ExecutesInjector(ChunkExecutorGuardInjector.class)
class ChunkExecutorGuardInjectorTest {
	private static final String TARGET = "net.minecraft.server.level.ServerChunkCache$MainThreadExecutor";
	private static final String TARGET_INTERNAL = "net/minecraft/server/level/ServerChunkCache$MainThreadExecutor";

	private static final String EVENT_LOOP = """
			package net.minecraft.util.thread;

			import java.util.ArrayDeque;
			import java.util.Queue;
			import java.util.concurrent.Executor;

			public abstract class BlockableEventLoop<R extends Runnable> implements Executor {
				private final Queue<Runnable> pending = new ArrayDeque<>();

				protected abstract Thread getRunningThread();

				@Override
				public void execute(Runnable task) {
					pending.add(task);
				}

				public int pendingTasks() {
					return pending.size();
				}

				public void runAllTasks() {
					for (Runnable task; (task = pending.poll()) != null; ) task.run();
				}
			}
			""";

	private static String chunkCache(String extraExecutorMember) {
		return """
				package net.minecraft.server.level;

				import net.minecraft.util.thread.BlockableEventLoop;

				public class ServerChunkCache {
					final Thread mainThread;
					final MainThreadExecutor mainThreadProcessor;

					public ServerChunkCache(Thread mainThread) {
						this.mainThread = mainThread;
						this.mainThreadProcessor = new MainThreadExecutor();
					}

					final class MainThreadExecutor extends BlockableEventLoop<Runnable> {
						@Override
						protected Thread getRunningThread() {
							return ServerChunkCache.this.mainThread;
						}
				%s
					}
				}
				""".formatted(extraExecutorMember);
	}

	private static Map<String, byte[]> standIns(Path work, String extraExecutorMember) throws Exception {
		return InjectorExecution.compile(work, Map.of(
				"net.minecraft.util.thread.BlockableEventLoop", EVENT_LOOP,
				"net.minecraft.server.level.ServerChunkCache", chunkCache(extraExecutorMember)));
	}

	/** The stand-ins with the executor transformed, defined; verified first so a bad body fails here, not at a call. */
	private static ClassLoader transformed(Map<String, byte[]> original) {
		byte[] executor = original.get(TARGET_INTERNAL);
		byte[] guarded = InjectorExecution.transform(new ChunkExecutorGuardInjector(), TARGET, executor, EnvType.SERVER);
		assertNotSame(executor, guarded, "the injector did not touch the chunk executor");
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(TARGET_INTERNAL, guarded);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(guarded, loader));
		return loader;
	}

	private static Object cacheOn(ClassLoader loader, Thread serverThread) throws Throwable {
		return InjectorExecution.construct(loader.loadClass("net.minecraft.server.level.ServerChunkCache"), serverThread);
	}

	private static Executor processorOf(Object cache) throws ReflectiveOperationException {
		Field field = cache.getClass().getDeclaredField("mainThreadProcessor");
		field.setAccessible(true);
		return (Executor) field.get(cache);
	}

	private static Thread deadThread(String name) throws InterruptedException {
		Thread thread = new Thread(() -> {
		}, name);
		thread.start();
		thread.join();
		assertFalse(thread.isAlive(), "the fixture has to be genuinely dead or the refusal below proves nothing");
		return thread;
	}

	/**
	 * The incident's own call, {@code supplyAsync} on a stopped server's executor. Untransformed it hands back a future
	 * that only the dead thread could complete, and the caller's {@code join()} parks forever; guarded, it throws to the
	 * caller before any future exists.
	 */
	@Test void aStoppedServersExecutorRefusesChunkWorkAndNamesItsThread(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = standIns(work, "");
		Thread stopped = deadThread("forbric-test-stopped-server");

		Executor guarded = processorOf(cacheOn(transformed(original), stopped));
		RejectedExecutionException refused = assertThrows(RejectedExecutionException.class,
				() -> CompletableFuture.supplyAsync(() -> "chunk", guarded));
		assertTrue(refused.getMessage().contains("forbric-test-stopped-server"),
				"the refusal must name the dead thread, which is what makes the crash report readable: " + refused.getMessage());
		assertEquals(0, InjectorExecution.invoke(guarded, "pendingTasks"), "refused work must not also be queued");

		// Control: the same stand-ins untransformed, which is also what -Dforbric.chunkExecutorGuard=off ships.
		Executor vanilla = processorOf(cacheOn(InjectorExecution.load(original), stopped));
		CompletableFuture<String> parked = CompletableFuture.supplyAsync(() -> "chunk", vanilla);
		assertFalse(parked.isDone(), "premise: untransformed, the work is accepted into a queue nothing will drain");
		assertEquals(1, InjectorExecution.invoke(vanilla, "pendingTasks"));
	}

	/** While the server's thread lives the guard is inert: work still reaches the event loop's queue and completes. */
	@Test void aRunningServersExecutorStillQueuesAndDrains(@TempDir Path work) throws Throwable {
		Executor guarded = processorOf(cacheOn(transformed(standIns(work, "")), Thread.currentThread()));
		CompletableFuture<String> chunk = CompletableFuture.supplyAsync(() -> "chunk", guarded);
		assertEquals(1, InjectorExecution.invoke(guarded, "pendingTasks"), "the synthesized execute did not reach super.execute");
		InjectorExecution.invoke(guarded, "runAllTasks");
		assertEquals("chunk", chunk.join());
	}

	/** Only the chunk executor: the event loop it extends serves every loop in the game, and the cache is not the anchor. */
	@Test void classesOtherThanTheChunkExecutorComeBackByteIdentical(@TempDir Path work) throws Exception {
		Map<String, byte[]> original = standIns(work, "");
		ChunkExecutorGuardInjector injector = new ChunkExecutorGuardInjector();
		for (String other : new String[] {"net/minecraft/util/thread/BlockableEventLoop", "net/minecraft/server/level/ServerChunkCache"}) {
			byte[] bytes = original.get(other);
			assertSame(bytes, InjectorExecution.transform(injector, other.replace('/', '.'), bytes, EnvType.SERVER), other);
		}
	}

	/** A second {@code execute(Runnable)} would make the class invalid, so an executor that has one is not edited. */
	@Test void anExecutorThatAlreadyDeclaresExecuteIsLeftAlone(@TempDir Path work) throws Exception {
		byte[] own = standIns(work, """
						@Override
						public void execute(Runnable task) {
							super.execute(task);
						}
				""").get(TARGET_INTERNAL);
		assertSame(own, InjectorExecution.transform(new ChunkExecutorGuardInjector(), TARGET, own, EnvType.SERVER));
	}
}
