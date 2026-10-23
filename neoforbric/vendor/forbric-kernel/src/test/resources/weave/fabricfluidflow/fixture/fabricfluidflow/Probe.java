package fixture.fabricfluidflow;

import net.fabricmc.fabric.api.block.v1.FluidFlowEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.redstone.Orientation;

/**
 * A listener denies flow at one position. Each of the three paths runs at an allowed and a denied position; per
 * position, the ticks each path scheduled and how many native interaction checks ran.
 */
public class Probe {
	public String run() {
		FluidFlowEvents.ALLOW.register((fluid, level, pos) -> !pos.name().equals("denied"));
		LiquidBlock water = new LiquidBlock(new Fluid("water"));
		return row(water, new BlockPos("allowed")) + " " + row(water, new BlockPos("denied"));
	}

	private static String row(LiquidBlock water, BlockPos pos) {
		Level level = new Level();
		water.onPlace(new BlockState(), level, pos, new BlockState(), false);
		int placed = level.ticks.size();
		water.neighborChanged(new BlockState(), level, pos, new Block(), new Orientation(), false);
		int changed = level.ticks.size() - placed;
		water.updateShape(new BlockState(), level, level, pos, Direction.UP, pos, new BlockState(), new RandomSource());
		int shaped = level.ticks.size() - placed - changed;
		return pos.name() + ":ticks=" + placed + "," + changed + "," + shaped + ",checks=" + level.interactionChecks;
	}
}
