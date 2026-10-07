package net.minecraft.subtypeowner;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;

/**
 * A game-side target in its merged shape (hand-written; in a game package so preflight judges it as the merged game).
 * Every parse appends "parse" to the trace and the guest handler appends "gate", so the trace each method returns says
 * whether, where and how often the handler ran.
 *
 * <p>The three descriptors differ on purpose: with one shared descriptor and -Dforbric.mixinRetarget.renameCensus=off,
 * MixinRetarget's renamed-body rule (R3) moves the loadMerged and loadTwice selectors onto loadMixed, the one sibling
 * that still calls Decoder.parse, before this stage ever sees them.
 */
public class EntryLoader {
	private static final DynamicOps<String> OPS = new DynamicOps<>() {
	};
	private final List<String> trace = new ArrayList<>();
	private final Codec<String> codec = new Codec<>() {
		@Override
		public <T> DataResult<String> parse(DynamicOps<T> ops, T input) {
			trace.add("parse");
			return DataResult.success(String.valueOf(input));
		}
	};

	/** One call, made through Codec: the very method a vanilla-compiled mod names as Decoder.parse. */
	public String loadMerged(String raw) {
		codec.parse(OPS, raw);
		return String.join(",", trace);
	}

	/** Vanilla's own Decoder call is still here beside the subtype's: Mixin matches it natively, nothing may move. */
	public String loadMixed(String raw, String fallback) {
		Decoder<String> decoder = codec;
		decoder.parse(OPS, raw);
		codec.parse(OPS, fallback);
		return String.join(",", trace);
	}

	/** Two calls through the subtype: which one the mod meant is not knowable, so nothing may move. */
	public String loadTwice(String[] pair) {
		codec.parse(OPS, pair[0]);
		codec.parse(OPS, pair[1]);
		return String.join(",", trace);
	}

	/** The harness probe: each shape on a fresh loader, so no trace leaks into another. */
	public String probe() {
		return "merged=" + new EntryLoader().loadMerged("a") + " mixed=" + new EntryLoader().loadMixed("a", "b")
				+ " twice=" + new EntryLoader().loadTwice(new String[] {"a", "b"});
	}
}
