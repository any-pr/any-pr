package net.minecraft.world.entity;

import java.util.ArrayList;
import java.util.List;

/** Stand-in: what an entity was looked at by, in order — the trace each probe returns. Hand-written; only the shape matters. */
public class Entity {
	public final List<String> seen = new ArrayList<>();
}
