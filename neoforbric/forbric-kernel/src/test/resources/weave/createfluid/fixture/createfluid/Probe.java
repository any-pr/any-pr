package fixture.createfluid;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;

/**
 * Places a fluid where the mod's registry reacts it ("lava") and where it does not ("water"), then updates each from a
 * neighbour: for every update, who decided and whether the fluid went on to flow.
 */
public class Probe {
	public String probe() {
		LiquidBlock fluid = new LiquidBlock();
		StringBuilder out = new StringBuilder();
		for (String update : new String[] {"place", "neighbor"}) {
			for (String where : new String[] {"lava", "water"}) {
				Level level = new Level(Set.of("lava"));
				if (update.equals("place")) fluid.place(level, new BlockPos(where));
				else fluid.neighbor(level, new BlockPos(where));
				out.append(out.isEmpty() ? "" : " | ").append(update).append(": ").append(String.join(", ", level.trail));
			}
		}
		return out.toString();
	}
}
