package com.mojang.datafixers.util;

/** Hand-written stand-in, not library code: the two members of a decode result. */
public record Pair<F, S>(F getFirst, S getSecond) {
	public static <F, S> Pair<F, S> of(F first, S second) {
		return new Pair<>(first, second);
	}
}
