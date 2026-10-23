package net.minecraftforge.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Fixture stand-in: answers whether one of its interactions handled the cell. */
public final class FluidInteractionRegistry {
	private FluidInteractionRegistry() {
	}

	public static boolean canInteract(Level level, BlockPos pos) {
		return level.nativelyHandled.contains(pos);
	}
}
