package net.neoforged.neoforge.common;

import fixture.createinjection.Trail;
import net.minecraft.world.level.entity.EntityAccess;

/** A stand-in for NeoForge's section-change event. */
public final class CommonHooks {
	private CommonHooks() {
	}

	public static void onEntityEnterSection(EntityAccess entity, long packedOldPos, long packedNewPos) {
		Trail.add("neoforge " + entity.name() + " " + packedOldPos + "->" + packedNewPos);
	}
}
