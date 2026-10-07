package net.minecraft.tags;

import net.minecraft.world.level.material.Fluid;

/** A stand-in for the two fluid tags. */
public final class FluidTags {
	public static final TagKey<Fluid> WATER = new TagKey<>("water");
	public static final TagKey<Fluid> LAVA = new TagKey<>("lava");

	private FluidTags() {
	}
}
