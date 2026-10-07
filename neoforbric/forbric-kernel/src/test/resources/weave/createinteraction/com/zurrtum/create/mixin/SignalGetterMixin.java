package com.zurrtum.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.foundation.block.WeakPowerControlBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Shaped like Create Fly's SignalGetterMixin, an interface mixin written against vanilla's getSignal: it wraps the
 * isRedstoneConductor call, so a block of the mod decides for itself whether weak power passes through it.
 */
@Mixin(SignalGetter.class)
public interface SignalGetterMixin {
	@WrapOperation(method = "getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isRedstoneConductor(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"))
	private boolean skip(BlockState state, BlockGetter blockView, BlockPos pos, Operation<Boolean> original,
			@Local(argsOnly = true) Direction direction) {
		Block block = state.getBlock();
		if (block instanceof WeakPowerControlBlock control) {
			return control.shouldCheckWeakPower(state, (SignalGetter) this, pos, direction);
		}
		return original.call(state, blockView, pos);
	}
}
