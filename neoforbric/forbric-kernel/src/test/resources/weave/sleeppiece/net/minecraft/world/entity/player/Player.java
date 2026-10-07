package net.minecraft.world.entity.player;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Unit;

/** Stand-in: the sleep itself, which ServerPlayer reaches only when its checks said yes. */
public class Player {
	public final StringBuilder trace = new StringBuilder();

	public enum BedSleepingProblem {
		NOT_POSSIBLE_HERE, TOO_FAR_AWAY, OBSTRUCTED, OTHER_PROBLEM, NOT_SAFE
	}

	public Either<BedSleepingProblem, Unit> startSleepInBed(BlockPos pos) {
		trace.append("slept;");
		return Either.right(Unit.INSTANCE);
	}
}
