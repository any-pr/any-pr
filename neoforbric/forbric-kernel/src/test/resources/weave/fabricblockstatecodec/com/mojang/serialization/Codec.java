package com.mojang.serialization;

import java.util.function.Function;

import com.mojang.datafixers.util.Pair;

/** Hand-written stand-in, not library code: decode, encode, and the one combinator the merged class builds with. */
public interface Codec<A> {
	<T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input);

	<T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix);

	default <S> Codec<S> flatComapMap(Function<? super A, ? extends S> to, Function<? super S, ? extends DataResult<? extends A>> from) {
		Codec<A> self = this;
		return new Codec<>() {
			@Override
			public <T> DataResult<Pair<S, T>> decode(DynamicOps<T> ops, T input) {
				DataResult<Pair<A, T>> read = self.decode(ops, input);
				return read.isError() ? DataResult.error(read.toString())
						: DataResult.success(Pair.of(to.apply(read.result().get().getFirst()), input));
			}

			@Override
			public <T> DataResult<T> encode(S input, DynamicOps<T> ops, T prefix) {
				return DataResult.error("not encoded in this fixture");
			}
		};
	}
}
