package com.mojang.serialization;

import java.util.Optional;

/** Hand-written stand-in, not library code: a value or an error message. */
public final class DataResult<R> {
	private final R value;
	private final String error;

	private DataResult(R value, String error) {
		this.value = value;
		this.error = error;
	}

	public static <R> DataResult<R> success(R value) {
		return new DataResult<>(value, null);
	}

	public static <R> DataResult<R> error(String message) {
		return new DataResult<>(null, message);
	}

	public boolean isError() {
		return error != null;
	}

	public Optional<R> result() {
		return Optional.ofNullable(value);
	}

	@Override
	public String toString() {
		return isError() ? "error(" + error + ")" : String.valueOf(value);
	}
}
