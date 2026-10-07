package carpet.mixins;

import carpet.CarpetSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A fluid rule in Carpet's renewable-blackstone shape: its class and handler names and the cancellable TAIL @Inject on
 * vanilla's shouldSpreadLiquid that answers false for a cell it converted. The rule itself is the fixture's own: lava
 * under blue ice turns to blackstone.
 */
@Mixin(LiquidBlock.class)
public abstract class LiquidBlock_renewableBlackstoneMixin {
	@Shadow @Final protected FlowingFluid fluid;

	@Shadow protected abstract void fizz(LevelAccessor level, BlockPos pos);

	@Inject(method = "shouldSpreadLiquid", at = @At("TAIL"), cancellable = true)
	private void receiveFluidToBlackstone(Level world, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (CarpetSettings.renewableBlackstone && fluid.is(FluidTags.LAVA) && world.getBlockState(pos.above()).is(Blocks.BLUE_ICE)) {
			world.setBlockAndUpdate(pos, Blocks.BLACKSTONE.defaultBlockState());
			fizz(world, pos);
			cir.setReturnValue(false);
		}
	}
}
