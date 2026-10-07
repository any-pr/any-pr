package net.minecraft.world.level.material;

import net.minecraft.tags.TagKey;

/** Fixture stand-in: a fluid and the one tag it carries. */
public class Fluid {
	private final TagKey tag;

	public Fluid(TagKey tag) {
		this.tag = tag;
	}

	public boolean is(TagKey key) {
		return tag.equals(key);
	}
}
