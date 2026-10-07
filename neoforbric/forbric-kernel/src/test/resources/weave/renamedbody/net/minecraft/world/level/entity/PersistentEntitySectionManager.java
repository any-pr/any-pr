package net.minecraft.world.level.entity;

/**
 * Hand-written stand-in with the SHAPE of one carrier-renames.txt row, no game code: NeoForge's addEntity posts its join
 * event and calls vanilla's body, which it renamed addEntityWithoutEvent and gave the same descriptor.
 */
public class PersistentEntitySectionManager {
	public final StringBuilder trace = new StringBuilder();

	public boolean addEntity(EntityAccess entity, boolean worldGenSpawned) {
		trace.append("event;");
		return addEntityWithoutEvent(entity, worldGenSpawned);
	}

	private boolean addEntityWithoutEvent(EntityAccess entity, boolean worldGenSpawned) {
		trace.append("body;");
		return Visibility.TICKING.isTicking();
	}
}
