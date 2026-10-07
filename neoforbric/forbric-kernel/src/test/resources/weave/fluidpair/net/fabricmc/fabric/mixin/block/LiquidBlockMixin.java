package net.fabricmc.fabric.mixin.block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.fabricmc.fabric.api.block.v1.FluidFlowEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hand-written stand-in, not fabric-api's code: a guest mixin in the shape of fabric-block-api's, where the ALLOW event
 * vetoes vanilla's spread check. Its handler has the same name and descriptor as Create Fly's beside it.
 */
@Mixin(LiquidBlock.class)
abstract class LiquidBlockMixin {
	@Inject(method = "shouldSpreadLiquid", at = @At("HEAD"), cancellable = true)
	private void shouldSpreadLiquid(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> info) {
		if (!FluidFlowEvents.ALLOW.invoker().allowFlow(level.getFluidState(pos), level, pos)) info.setReturnValue(false);
	}
}
