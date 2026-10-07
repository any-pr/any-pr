package net.minecraft.world.level;

import java.util.ArrayList;
import java.util.List;

/** A stand-in that records the entities added to it, in order. */
public class Level implements ServerLevelAccessor {
	public final List<String> entities = new ArrayList<>();

	@Override
	public void addFreshEntity(String entity) {
		entities.add(entity);
	}
}
