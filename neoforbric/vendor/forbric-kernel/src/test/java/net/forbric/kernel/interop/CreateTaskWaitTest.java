/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.interop;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CreateTaskWaitTest {
	private static class Pool {
		private final AtomicBoolean running=new AtomicBoolean(true);
		private final java.util.Deque<Runnable> taskQueue=new ConcurrentLinkedDeque<>();
	}
	@Test void notificationBeforeWaitingCannotStrandAStoppedWorkerOrQueuedTask() {
		Pool pool=new Pool();Object notifier=new Object();
		pool.running.set(false);
		synchronized(notifier){notifier.notifyAll();}
		assertTimeoutPreemptively(Duration.ofSeconds(1),()->CreateTaskWait.awaitNotification(notifier,pool));
		pool.running.set(true);pool.taskQueue.add(()->{});
		synchronized(notifier){notifier.notifyAll();}
		assertTimeoutPreemptively(Duration.ofSeconds(1),()->CreateTaskWait.awaitNotification(notifier,pool));
	}
	@Test void anIdleWorkerWaitsAndAStopUnderTheSameMonitorWakesIt() throws Exception {
		Pool pool=new Pool();Object notifier=new Object();
		Thread worker=new Thread(()->CreateTaskWait.awaitNotification(notifier,pool));worker.setDaemon(true);worker.start();
		try {
			assertTimeoutPreemptively(Duration.ofSeconds(2),()->{while(worker.getState()!=Thread.State.WAITING)Thread.sleep(1);});
			synchronized(notifier){pool.running.set(false);notifier.notifyAll();}
			worker.join(1000);assertFalse(worker.isAlive());
		}finally{pool.running.set(false);synchronized(notifier){notifier.notifyAll();}worker.interrupt();worker.join(1000);}
	}
}
