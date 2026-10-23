package carpet.mixins;

import carpet.CarpetSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A fill-without-updates hook in Carpet's shape: its class and handler names, and both injectors on vanilla's
 * setBlock, a @ModifyConstant of the UPDATE_KNOWN_SHAPE bit and a @Redirect of the neighbour update. The bodies are
 * the fixture's own.
 */
@Mixin(Level.class)
public class Level_fillUpdatesMixin {
	@ModifyConstant(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
			constant = @Constant(intValue = 16))
	private int addFillUpdatesInt(int original) {
		return CarpetSettings.impendingFillSkipUpdates.get() ? -1 : original;
	}

	@Redirect(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V"))
	private void updateNeighborsMaybe(Level world, BlockPos blockPos, Block block) {
		if (!CarpetSettings.impendingFillSkipUpdates.get()) world.updateNeighborsAt(blockPos, block);
	}
}
