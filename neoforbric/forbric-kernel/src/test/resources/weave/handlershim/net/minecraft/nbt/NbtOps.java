package net.minecraft.nbt;

import java.util.ArrayList;
import java.util.List;

import com.mojang.datafixers.util.Pair;

/**
 * A stand-in for the merged base's NbtOps, reduced to the one lambda lambda-permutations.txt has a row for: vanilla's
 * takes (List, CompoundTag, Pair), the merged one takes the same three captures with the compound first.
 */
public class NbtOps {
	public String probe() {
		List<String> trail = new ArrayList<>();
		lambda$mergeToMap$3(new CompoundTag("root"), trail, Pair.of("key", "value"));
		return String.join(" | ", trail);
	}

	// Spelled out rather than written as a lambda: javac numbers lambdas by its own count, and the name and the
	// descriptor have to be exactly the census row's merged side.
	private static void lambda$mergeToMap$3(CompoundTag tag, List<String> trail, Pair<String, String> entry) {
		trail.add("merged " + entry.getFirst() + "=" + entry.getSecond() + " into " + tag.name());
	}
}
