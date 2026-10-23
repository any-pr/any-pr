package net.fabricmc.fabric.api.block.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.FluidState;

/** Hand-written stand-in for fabric-block-api's fluid flow event: any listener can deny the flow. */
public final class FluidFlowEvents {
	public static final Event<Allow> ALLOW = new Event<>(listeners -> (fluid, level, pos) ->
			listeners.stream().allMatch(listener -> listener.allowFlow(fluid, level, pos)));

	private FluidFlowEvents() {
	}

	public interface Allow {
		boolean allowFlow(FluidState fluid, LevelAccessor level, BlockPos pos);
	}
}
