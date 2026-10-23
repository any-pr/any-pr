/*
 * Copyright 2026 The Forbric Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.forbric.kernel.runtime;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.common.extensions.IOwnedSpawner;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * The spawner-finalization seam: NeoForge's {@code EventHooks.finalizeMobSpawnSpawner} under the kernel's own
 * name, in both shapes the redirected spawner call sites use.
 */
public final class KernelSpawnerFinalize {

	private KernelSpawnerFinalize() {
	}

	/** The seven-argument shape, matching NeoForge's own signature. */
	public static FinalizeSpawnEvent finalizeMobSpawnSpawner(Mob mob, ServerLevelAccessor level,
			DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data, IOwnedSpawner spawner,
			boolean flag) {
		return EventHooks.finalizeMobSpawnSpawner(mob, level, difficulty, reason, data, spawner, flag);
	}

	/**
	 * The eight-argument shape; the added {@code ValueInput} is the caller's verified {@code TagValueInput.create}
	 * result, accepted so the redirect needs no argument pruning.
	 */
	public static FinalizeSpawnEvent finalizeMobSpawnSpawner(Mob mob, ServerLevelAccessor level,
			DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data, IOwnedSpawner spawner,
			boolean initialize, ValueInput input) {
		return EventHooks.finalizeMobSpawnSpawner(mob, level, difficulty, reason, data, spawner, initialize);
	}
}
