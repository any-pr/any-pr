package fixture.fluidpair;

import java.util.Set;

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
 * Hand-written probe, not game code. One ALLOW listener records every call into the level's trail and denies flow at
 * "denied". Three positions — "lava", where Create's registry reacts the fluid; "water", where nothing does; "denied" —
 * each get the three updates a liquid block receives, each on a fresh level: per update, the whole trail.
 */
public class Probe {
	public String probe() {
		FluidFlowEvents.ALLOW.register((fluid, level, pos) -> {
			boolean allow = !pos.name().equals("denied");
			((Level) level).trail.add((allow ? "allow " : "deny ") + pos.name());
			return allow;
		});
		LiquidBlock water = new LiquidBlock(new Fluid("water"));
		StringBuilder out = new StringBuilder();
		for (String where : new String[] {"lava", "water", "denied"}) {
			BlockPos pos = new BlockPos(where);
			for (String update : new String[] {"place", "neighbor", "shape"}) {
				Level level = new Level(Set.of("lava"));
				switch (update) {
					case "place" -> water.onPlace(new BlockState(), level, pos, new BlockState(), false);
					case "neighbor" -> water.neighborChanged(new BlockState(), level, pos, new Block(), new Orientation(), false);
					default -> water.updateShape(new BlockState(), level, level, pos, Direction.UP, pos, new BlockState(), new RandomSource());
				}
				out.append(out.isEmpty() ? "" : " | ").append(update).append(' ').append(where).append(": ")
						.append(level.trail.isEmpty() ? "-" : String.join(", ", level.trail));
			}
		}
		return out.toString();
	}
}
