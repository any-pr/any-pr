package net.neoforged.neoforge.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Hand-written stand-in, not NeoForge code: the interaction check NeoForge's neighborChanged makes. Nothing interacts. */
public final class FluidInteractionRegistry {
	private FluidInteractionRegistry() {
	}

	public static boolean canInteract(Level level, BlockPos pos) {
		level.interactionChecks++;
		return false;
	}
}
