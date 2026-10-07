package net.neoforged.neoforge.event;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;

/** Stand-in for NeoForge's hook: CanPlayerSleepEvent sees the checks' problem and, unheard, leaves it. */
public final class EventHooks {
	private EventHooks() {
	}

	public static Either<BedSleepingProblem, Unit> canPlayerStartSleeping(ServerPlayer player, BlockPos pos, Either<BedSleepingProblem, Unit> vanilla) {
		BedSleepingProblem problem = vanilla.left().orElse(null);
		player.trace.append("event:").append(problem).append(';');
		return problem != null ? Either.left(problem) : Either.right(Unit.INSTANCE);
	}
}
