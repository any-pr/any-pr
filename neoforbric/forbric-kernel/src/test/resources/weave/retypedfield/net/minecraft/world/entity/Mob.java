package net.minecraft.world.entity;

/** Stand-in: the merged type of the widened field. Declares lookAt, which Monster inherits without redeclaring. */
public class Mob extends Entity {
	private final String name;
	private Entity vehicle;

	public Mob(String name) {
		this.name = name;
	}

	public void lookAt(Entity target, float yaw, float pitch) {
		target.seen.add("look:" + name);
	}

	public Entity getControlledVehicle() {
		return vehicle;
	}

	public Mob riding(Entity vehicle) {
		this.vehicle = vehicle;
		return this;
	}
}
