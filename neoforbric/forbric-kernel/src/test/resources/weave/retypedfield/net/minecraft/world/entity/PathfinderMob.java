package net.minecraft.world.entity;

/** Stand-in: between Mob and Monster in vanilla's hierarchy, and like vanilla's it does not redeclare lookAt. */
public class PathfinderMob extends Mob {
	public PathfinderMob(String name) {
		super(name);
	}
}
