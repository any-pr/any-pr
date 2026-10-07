package com.mojang.blaze3d;

/**
 * A stand-in written for the weave test, named after the game class whose row in {@code vanilla-early-returns.txt}
 * it has to match. Only the SHAPE of {@code least(int)} matters: a conditional that jumps to the one return at the
 * end, the shape the carriers' recompile leaves where vanilla returned early. The row keys the edge that leaves the
 * block reading {@code INT}, so that read is the one thing this has to share with the game; the condition is its own.
 */
public final class IndexType {
	public static final IndexType SHORT = new IndexType("SHORT");
	public static final IndexType INT = new IndexType("INT");

	private final String name;

	private IndexType(String name) {
		this.name = name;
	}

	public static IndexType least(int count) {
		return count > 0xFFFF ? INT : SHORT;
	}

	@Override
	public String toString() {
		return name;
	}
}
