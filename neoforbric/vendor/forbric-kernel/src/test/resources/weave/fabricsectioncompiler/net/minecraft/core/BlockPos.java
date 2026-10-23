package net.minecraft.core;

import java.util.ArrayList;
import java.util.List;

/** Hand-written stand-in, not game code: positions along one axis, enough to walk a section. */
public record BlockPos(int x) {
	public static Iterable<BlockPos> betweenClosed(BlockPos from, BlockPos to) {
		List<BlockPos> positions = new ArrayList<>();
		for (int x = from.x(); x <= to.x(); x++) positions.add(new BlockPos(x));
		return positions;
	}
}
