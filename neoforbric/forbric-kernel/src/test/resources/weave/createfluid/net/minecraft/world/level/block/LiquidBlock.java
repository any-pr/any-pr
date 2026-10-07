package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;

/**
 * A stand-in for the merged base's LiquidBlock: onPlace asks MinecraftForge's interaction registry and neighborChanged
 * NeoForge's, each once, and schedules the flow only when neither reacted. Vanilla's shouldSpreadLiquid is still
 * declared, but nothing calls it any more.
 */
public class LiquidBlock extends Block {
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!net.minecraftforge.fluids.FluidInteractionRegistry.canInteract(level, pos)) {
			level.scheduleTick(pos);
		}
	}

	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation,
			boolean movedByPiston) {
		if (!net.neoforged.neoforge.fluids.FluidInteractionRegistry.canInteract(level, pos)) {
			level.scheduleTick(pos);
		}
	}

	private boolean shouldSpreadLiquid(Level level, BlockPos pos, BlockState state) {
		return true;
	}

	/** The two block updates a placed fluid receives, as the game makes them. */
	public void place(Level level, BlockPos pos) {
		onPlace(new BlockState(), level, pos, new BlockState(), false);
	}

	public void neighbor(Level level, BlockPos pos) {
		neighborChanged(new BlockState(), level, pos, new Block(), new Orientation(), false);
	}
}
