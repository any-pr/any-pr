package net.minecraft.world.level.block.state;

import net.minecraft.world.level.block.state.properties.Property;

/** Stand-in: every property reads as the same facing. */
public final class BlockState {
	@SuppressWarnings("unchecked")
	public <T extends Comparable<T>> T getValue(Property<T> property) {
		return (T) "north";
	}
}
