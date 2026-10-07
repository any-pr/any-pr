package com.mojang.datafixers.util;

/** A stand-in with the accessors the fixture uses; the descriptor only needs the type to exist. */
public final class Pair<F, S> {
	private final F first;
	private final S second;

	private Pair(F first, S second) {
		this.first = first;
		this.second = second;
	}

	public static <F, S> Pair<F, S> of(F first, S second) {
		return new Pair<>(first, second);
	}

	public F getFirst() {
		return first;
	}

	public S getSecond() {
		return second;
	}
}
