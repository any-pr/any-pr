package com.mojang.serialization;

/** Hand-written stand-in, not library code: a map read through the ops. */
public interface MapLike<T> {
	T get(String key);
}
