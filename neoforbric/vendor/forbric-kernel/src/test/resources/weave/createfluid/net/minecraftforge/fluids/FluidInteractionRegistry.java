package net.minecraftforge.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** A stand-in for MinecraftForge's registry: it knows no interaction here. */
public final class FluidInteractionRegistry {
	private FluidInteractionRegistry() {
	}

	public static boolean canInteract(Level level, BlockPos pos) {
		level.trail.add("forge none " + pos.name());
		return false;
	}
}
