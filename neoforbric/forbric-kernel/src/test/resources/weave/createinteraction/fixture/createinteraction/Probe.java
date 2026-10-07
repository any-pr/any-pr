package fixture.createinteraction;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Reads the signal at a stone block and at the mod's gearshift, each next to a strongly powered neighbour. */
public class Probe {
	public String probe() {
		Map<String, BlockState> blocks = Map.of("stone", new BlockState(new Block("stone", true)), "gearshift",
				new BlockState(new Gearshift()));
		SignalGetter level = new SignalGetter() {
			@Override
			public BlockState getBlockState(BlockPos pos) {
				return blocks.get(pos.name());
			}

			@Override
			public int getDirectSignalTo(BlockPos pos) {
				return 15;
			}
		};
		return "stone " + level.getSignal(new BlockPos("stone"), Direction.NORTH) + " | gearshift "
				+ level.getSignal(new BlockPos("gearshift"), Direction.NORTH);
	}
}
