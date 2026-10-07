package com.google.common.collect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A stand-in for the one Guava call the mixin anchors on, ImmutableList.builder(); the fixture compiles against Mixin
 * and ASM only. A list that only says what it holds.
 */
public final class ImmutableList<E> {
	private final List<E> elements;

	private ImmutableList(List<E> elements) {
		this.elements = Collections.unmodifiableList(elements);
	}

	public static <E> Builder<E> builder() {
		return new Builder<>();
	}

	public List<E> asList() {
		return elements;
	}

	public static final class Builder<E> {
		private final List<E> elements = new ArrayList<>();

		public Builder<E> addAll(Iterable<? extends E> more) {
			for (E element : more) elements.add(element);
			return this;
		}

		public ImmutableList<E> build() {
			return new ImmutableList<>(new ArrayList<>(elements));
		}
	}
}
