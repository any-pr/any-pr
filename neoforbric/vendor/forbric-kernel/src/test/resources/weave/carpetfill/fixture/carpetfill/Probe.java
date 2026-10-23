package fixture.carpetfill;

import carpet.CarpetSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Sets one block normally and one the way a fill with updates turned off does; reports the updates that ran. */
public class Probe {
	public String probe() {
		Level level = new Level();
		BlockState stone = new BlockState(new Block("minecraft:stone"));
		level.setBlock(new BlockPos(1, 2, 3), stone, 3, 512);
		CarpetSettings.impendingFillSkipUpdates.set(true);
		try {
			level.setBlock(new BlockPos(4, 5, 6), stone, 3, 512);
		} finally {
			CarpetSettings.impendingFillSkipUpdates.set(false);
		}
		return String.valueOf(level.trace);
	}
}
