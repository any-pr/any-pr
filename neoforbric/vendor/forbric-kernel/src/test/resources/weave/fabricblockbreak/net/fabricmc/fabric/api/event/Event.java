package net.fabricmc.fabric.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Hand-written stand-in for fabric-api's event: listeners, and one invoker that asks them all. */
public final class Event<T> {
	private final Function<List<T>, T> combine;
	private final List<T> listeners = new ArrayList<>();

	public Event(Function<List<T>, T> combine) {
		this.combine = combine;
	}

	public void register(T listener) {
		listeners.add(listener);
	}

	public T invoker() {
		return combine.apply(List.copyOf(listeners));
	}
}
