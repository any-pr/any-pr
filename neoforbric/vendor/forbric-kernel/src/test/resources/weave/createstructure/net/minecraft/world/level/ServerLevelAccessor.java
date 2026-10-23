package net.minecraft.world.level;

/** A stand-in: where a placed structure's entities are added. */
public interface ServerLevelAccessor {
	void addFreshEntity(String entity);
}
