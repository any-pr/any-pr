package net.minecraft.server.level;

import java.util.function.Supplier;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Hand-written stand-in with the SHAPE of the carrier-renames.txt pair startSleepInBed -> lambda$startSleepInBed$0 |
 * PIECE | LEFT, no game code: NeoForge moved vanilla's checks into a lambda, hands its answer to its hook, returns it when
 * it names a problem and only then sleeps. Vanilla's checks return their problem from startSleepInBed itself.
 */
public class ServerPlayer extends Player {
	public static final class RespawnConfig {
	}

	private static final Property<String> FACING = new Property<>();
	private final BlockState state = new BlockState();

	public boolean avian, noDirection;

	@Override
	public Either<Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos pos) {
		Either<Player.BedSleepingProblem, Unit> result = ((Supplier<Either<Player.BedSleepingProblem, Unit>>) () -> {
			trace.append("checks;");
			String facing = state.getValue(FACING);
			if (pos == null || facing == null) return Either.left(Player.BedSleepingProblem.OTHER_PROBLEM);
			setRespawnPosition(new RespawnConfig(), true);
			return Either.right(Unit.INSTANCE);
		}).get();
		result = EventHooks.canPlayerStartSleeping(this, pos, result);
		if (result.left().isPresent()) return result;
		return super.startSleepInBed(pos);
	}

	public void setRespawnPosition(RespawnConfig config, boolean message) {
		trace.append("respawn;");
	}
}
