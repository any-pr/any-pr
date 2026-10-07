package com.mojang.serialization;

/** Stand-in result holder; the fixture only needs parse to return this type. */
public final class DataResult<R> {
	private final R value;

	private DataResult(R value) {
		this.value = value;
	}

	public static <R> DataResult<R> success(R value) {
		return new DataResult<>(value);
	}

	public R value() {
		return value;
	}
}
