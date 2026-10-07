package com.mojang.datafixers.util;

import java.util.Optional;

/** Hand-written stand-in for DataFixerUpper's Either, no library code: {@code left()} is {@code Optional.of}, as DFU's is. */
public abstract class Either<L, R> {
	public static <L, R> Either<L, R> left(L value) {
		return new Left<>(value);
	}

	public static <L, R> Either<L, R> right(R value) {
		return new Right<>(value);
	}

	public abstract Optional<L> left();

	public abstract Optional<R> right();

	private static final class Left<L, R> extends Either<L, R> {
		private final L value;

		Left(L value) {
			this.value = value;
		}

		@Override
		public Optional<L> left() {
			return Optional.of(value);
		}

		@Override
		public Optional<R> right() {
			return Optional.empty();
		}

		@Override
		public String toString() {
			return "left(" + value + ")";
		}
	}

	private static final class Right<L, R> extends Either<L, R> {
		private final R value;

		Right(R value) {
			this.value = value;
		}

		@Override
		public Optional<L> left() {
			return Optional.empty();
		}

		@Override
		public Optional<R> right() {
			return Optional.of(value);
		}

		@Override
		public String toString() {
			return "right(" + value + ")";
		}
	}
}
