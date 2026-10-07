package net.minecraftforge.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Hand-written stand-in, not Forge code: the interaction check MinecraftForge's onPlace makes. Nothing interacts. */
public final class FluidInteractionRegistry {
	private FluidInteractionRegistry() {
	}

	public static boolean canInteract(Level level, BlockPos pos) {
		level.interactionChecks++;
		return false;
	}
}
