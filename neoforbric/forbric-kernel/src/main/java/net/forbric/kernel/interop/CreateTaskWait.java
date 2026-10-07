/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.interop;

import java.lang.reflect.Field;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

/** Check the worker's predicate under the same monitor as task/stop notification, preventing lost wakeups. */
public final class CreateTaskWait {
	private record Fields(Field running, Field queue) { }
	private static final ClassValue<Fields> FIELDS = new ClassValue<>() {
		@Override protected Fields computeValue(Class<?> type) {
			try {
				Field running=type.getDeclaredField("running"), queue=type.getDeclaredField("taskQueue");
				running.setAccessible(true);queue.setAccessible(true);return new Fields(running,queue);
			} catch(ReflectiveOperationException e){throw new IllegalStateException("Flywheel worker fields changed",e);}
		}
	};
	private CreateTaskWait() { }
	public static void awaitNotification(Object notifier,Object executor) {
		Fields fields=FIELDS.get(executor.getClass());
		synchronized(notifier) {
			try {
				if(((AtomicBoolean)fields.running().get(executor)).get() && ((Deque<?>)fields.queue().get(executor)).isEmpty()) notifier.wait();
			} catch(InterruptedException interrupted){/* Native workers consume interruption and recheck their run predicate. */}
			catch(IllegalAccessException e){throw new IllegalStateException("Cannot inspect Flywheel worker state",e);}
		}
	}
}
