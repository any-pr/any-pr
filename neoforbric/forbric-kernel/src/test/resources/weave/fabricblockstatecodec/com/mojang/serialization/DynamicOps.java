package com.mojang.serialization;

import java.util.stream.Stream;

/** Hand-written stand-in, not library code: only the two reads the kernel's codec makes. */
public interface DynamicOps<T> {
	DataResult<Stream<T>> getStream(T input);

	DataResult<MapLike<T>> getMap(T input);
}
