package net.minecraft.world.level.material;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;

/** A stand-in: no fluid, no resistance. */
public class FluidState {
	public float getExplosionResistance(BlockGetter level, BlockPos pos, Explosion explosion) {
		return 0.0F;
	}
}
