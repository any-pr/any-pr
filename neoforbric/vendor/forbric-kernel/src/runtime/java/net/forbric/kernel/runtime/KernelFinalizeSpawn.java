/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.runtime;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.common.extensions.IOwnedSpawner;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Where the kernel sends a mob's finalization: NeoForge's own hooks, which post the event and finalize exactly
 * once (NativeCoremodParity redirects the calls here).
 */
public final class KernelFinalizeSpawn {
	private KernelFinalizeSpawn() {
	}

	/** The redirect target of every {@code Mob.finalizeSpawn} call in NeoForge's listed classes. */
	public static SpawnGroupData finalizeMobSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
			EntitySpawnReason reason, SpawnGroupData data) {
		return EventHooks.finalizeMobSpawn(mob, level, difficulty, reason, data);
	}

	/** TrialSpawner's call, routed to NeoForge's spawner hook with its own initialize flag. */
	public static FinalizeSpawnEvent finalizeTrialSpawner(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
			EntitySpawnReason reason, SpawnGroupData data, IOwnedSpawner spawner, boolean initialize) {
		return EventHooks.finalizeMobSpawnSpawner(mob, level, difficulty, reason, data, spawner, initialize);
	}
}
