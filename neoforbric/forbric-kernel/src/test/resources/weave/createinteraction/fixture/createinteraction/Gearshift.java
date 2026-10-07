package fixture.createinteraction;

import com.zurrtum.create.foundation.block.WeakPowerControlBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A block of the mod: a full, conducting block that still never passes weak power on. */
public class Gearshift extends Block implements WeakPowerControlBlock {
	public Gearshift() {
		super("gearshift", true);
	}

	@Override
	public boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction side) {
		return false;
	}
}
