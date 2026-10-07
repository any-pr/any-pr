package net.minecraft.world.level.entity;

/** A stand-in: an entity's name and the section it is in now. */
public interface EntityAccess {
	String name();

	long sectionKey();
}
