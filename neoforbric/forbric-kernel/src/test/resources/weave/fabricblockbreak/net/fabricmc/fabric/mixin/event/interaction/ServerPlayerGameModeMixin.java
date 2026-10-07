package net.fabricmc.fabric.mixin.event.interaction;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Synthetic guest mixin in the shape of fabric-events-interaction's: AFTER fires at vanilla's {@code Block.destroy}
 * call in {@code destroyBlock}, with the block entity and adjusted state taken by name.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
	@Shadow
	protected ServerLevel level;
	@Shadow
	@Final
	protected ServerPlayer player;

	@Inject(method = "destroyBlock", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/Block;destroy(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
	private void onBlockBroken(BlockPos pos, CallbackInfoReturnable<Boolean> info, @Local(name = "blockEntity") BlockEntity blockEntity,
			@Local(name = "adjustedState") BlockState state) {
		PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, blockEntity);
	}
}
