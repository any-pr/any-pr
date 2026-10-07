package com.mojang.serialization;

/** Stand-in for the decoder type a vanilla-compiled mod names in its INVOKE target. Hand-written; only the shape matters. */
public interface Decoder<A> {
	<T> DataResult<A> parse(DynamicOps<T> ops, T input);
}
