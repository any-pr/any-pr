/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.api.EnvType;

/**
 * {@link CreateWorkerWaitInjector}'s output, run: a Flywheel worker told to stop before it parks no longer parks
 * forever, and a live one with nothing to do still parks until it is notified.
 *
 * <p>The stand-ins carry only what the edit keys on and what the real {@code CreateTaskWait} reads: the pool's
 * {@code running} flag and {@code taskQueue}, its notifier, and an inner worker whose {@code spinThenWait} calls
 * {@code awaitNotification}. Each test runs the worker on its own thread and reads that thread's state, because the
 * defect is a thread that never comes back.
 */
@ExecutesInjector(CreateWorkerWaitInjector.class)
class CreateWorkerWaitInjectorTest {
	private static final String PACKAGE = "com.zurrtum.create.client.flywheel.impl.task";
	private static final String POOL = PACKAGE + ".ParallelTaskExecutor";
	private static final String WORKER_INTERNAL = "com/zurrtum/create/client/flywheel/impl/task/ParallelTaskExecutor$WorkerThread";
	private static final long PATIENCE_SECONDS = 10;

	private static final String NOTIFIER_SOURCE = """
			package com.zurrtum.create.client.flywheel.impl.task;

			public class ThreadGroupNotifier {
				public void awaitNotification() {
					synchronized (this) {
						try {
							wait();
						} catch (InterruptedException interrupted) {
							Thread.currentThread().interrupt();
						}
					}
				}

				public void postNotification() {
					synchronized (this) {
						notifyAll();
					}
				}
			}
			""";

	/** {@code spinThenWait} parks without rechecking, which is the window a stop notification falls into. */
	private static String poolSource(String workerBody) {
		return """
				package com.zurrtum.create.client.flywheel.impl.task;

				import java.util.ArrayDeque;
				import java.util.Deque;
				import java.util.concurrent.atomic.AtomicBoolean;

				public class ParallelTaskExecutor {
					final AtomicBoolean running = new AtomicBoolean(true);
					final Deque<Runnable> taskQueue = new ArrayDeque<>();
					final ThreadGroupNotifier notifier = new ThreadGroupNotifier();

					public Runnable worker() {
						return new WorkerThread();
					}

					public void stop() {
						running.set(false);
						notifier.postNotification();
					}

					public void queue(Runnable task) {
						taskQueue.add(task);
					}

					class WorkerThread implements Runnable {
						@Override
						public void run() {
							spinThenWait();
						}

						void spinThenWait() {
				%s
						}
					}
				}
				""".formatted(workerBody);
	}

	private static final String PARK = "\t\t\t\tParallelTaskExecutor.this.notifier.awaitNotification();";

	private static Map<String, byte[]> standIns(Path work, String workerBody) throws Exception {
		return InjectorExecution.compile(work, Map.of(
				PACKAGE + ".ThreadGroupNotifier", NOTIFIER_SOURCE,
				POOL, poolSource(workerBody)));
	}

	private static ClassLoader transformed(Map<String, byte[]> original) {
		byte[] worker = original.get(WORKER_INTERNAL);
		byte[] edited = InjectorExecution.transform(new CreateWorkerWaitInjector(), CreateWorkerWaitInjector.TARGET, worker,
				EnvType.CLIENT);
		assertNotSame(worker, edited, "the injector did not touch the worker");
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(WORKER_INTERNAL, edited);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(edited, loader));
		return loader;
	}

	private static Thread start(Object pool, String name) throws Throwable {
		Thread thread = new Thread((Runnable) InjectorExecution.invoke(pool, "worker"), name);
		thread.setDaemon(true);
		thread.start();
		return thread;
	}

	private static void awaitState(Thread thread, Thread.State state) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PATIENCE_SECONDS);
		while (thread.getState() != state) {
			assertTrue(System.nanoTime() < deadline, thread.getName() + " never reached " + state + ", is " + thread.getState());
			Thread.sleep(5);
		}
	}

	/**
	 * The hang: the pool stopped (and notified) before the worker parked. Untransformed the worker waits for a
	 * notification that already happened; transformed it reads {@code running} under the notifier's monitor and leaves.
	 */
	@Test void aWorkerWhosePoolAlreadyStoppedReturnsInsteadOfWaitingForever(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = standIns(work, PARK);

		Object stoppedVanilla = InjectorExecution.construct(InjectorExecution.load(original).loadClass(POOL));
		InjectorExecution.invoke(stoppedVanilla, "stop");
		Thread lost = start(stoppedVanilla, "forbric-test-flywheel-untransformed");
		try {
			awaitState(lost, Thread.State.WAITING);
			assertTrue(lost.isAlive(), "premise: untransformed, the stopped worker is parked with nobody left to wake it");
		} finally {
			lost.interrupt();
			lost.join(TimeUnit.SECONDS.toMillis(PATIENCE_SECONDS));
		}

		Object stopped = InjectorExecution.construct(transformed(original).loadClass(POOL));
		InjectorExecution.invoke(stopped, "stop");
		Thread worker = start(stopped, "forbric-test-flywheel-stopped");
		worker.join(TimeUnit.SECONDS.toMillis(PATIENCE_SECONDS));
		assertFalse(worker.isAlive(), "a stopped pool's worker must come back, or the client cannot exit");
	}

	@Test void aWorkerWithQueuedWorkDoesNotPark(@TempDir Path work) throws Throwable {
		Object pool = InjectorExecution.construct(transformed(standIns(work, PARK)).loadClass(POOL));
		InjectorExecution.invoke(pool, "queue", (Runnable) () -> { });
		Thread worker = start(pool, "forbric-test-flywheel-busy");
		worker.join(TimeUnit.SECONDS.toMillis(PATIENCE_SECONDS));
		assertFalse(worker.isAlive(), "work is waiting, so the worker must go back for it instead of parking");
	}

	/** The repair is not "never wait": a live pool with nothing queued still parks its worker until it is told. */
	@Test void anIdleWorkerStillParksUntilItIsNotified(@TempDir Path work) throws Throwable {
		Object pool = InjectorExecution.construct(transformed(standIns(work, PARK)).loadClass(POOL));
		Thread worker = start(pool, "forbric-test-flywheel-idle");
		awaitState(worker, Thread.State.WAITING);
		InjectorExecution.invoke(pool, "stop");
		worker.join(TimeUnit.SECONDS.toMillis(PATIENCE_SECONDS));
		assertFalse(worker.isAlive(), "the stop notification must still wake a parked worker");
	}

	@Test void onlyTheWorkerIsEditedAndOnlyWhenItHasOneParkAndAnOuterPool(@TempDir Path work) throws Exception {
		Map<String, byte[]> original = standIns(work, PARK);
		CreateWorkerWaitInjector injector = new CreateWorkerWaitInjector();
		for (String other : new String[] {POOL, PACKAGE + ".ThreadGroupNotifier"}) {
			byte[] bytes = original.get(other.replace('.', '/'));
			assertSame(bytes, InjectorExecution.transform(injector, other, bytes, EnvType.CLIENT), other);
		}
		byte[] twice = standIns(work, PARK + "\n" + PARK).get(WORKER_INTERNAL);
		assertSame(twice, InjectorExecution.transform(injector, CreateWorkerWaitInjector.TARGET, twice, EnvType.CLIENT),
				"two parks are a different method; the edit must not guess which one is the idle wait");
		// No use of the pool, so javac leaves out this$0: there is nothing to hand CreateTaskWait.
		byte[] detached = standIns(work, "").get(WORKER_INTERNAL);
		assertSame(detached, InjectorExecution.transform(injector, CreateWorkerWaitInjector.TARGET, detached, EnvType.CLIENT));
	}
}
