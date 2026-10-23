package net.fabricmc.fabric.mixin.event.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A pick-block wrap written the way a Fabric mod writes it: against vanilla's getCloneItemStack(level, pos,
 * includeData). Its class and handler name are the reviewed MixinWrapOperationShim row; the body is the fixture's own.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@WrapOperation(method = "handlePickItemFromBlock", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getCloneItemStack(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack onPickItemFromBlock(BlockState state, LevelReader level, BlockPos pos, boolean includeData,
			Operation<ItemStack> operation, @Local(argsOnly = true) ServerboundPickItemFromBlockPacket packet) {
		// Changes one argument it was handed: the changed position, not the call site's, must reach the call.
		ItemStack original = operation.call(state, level, pos.above(), includeData);
		return new ItemStack("event[level=" + level + " pos=" + pos + " data=" + includeData + " " + packet + "] -> " + original);
	}
}
