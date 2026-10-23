package net.minecraft.world.level;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * A stand-in for the merged base's ExplosionDamageCalculator: NeoForge asks the state, with its level, position and
 * explosion, where vanilla asks the block's getExplosionResistance().
 */
public class ExplosionDamageCalculator {
	public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state,
			FluidState fluid) {
		return Optional.of(Math.max(state.getExplosionResistance(level, pos, explosion), fluid.getExplosionResistance(level, pos, explosion)));
	}
}
