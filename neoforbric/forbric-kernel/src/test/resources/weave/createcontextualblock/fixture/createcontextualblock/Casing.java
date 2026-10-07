package fixture.createcontextualblock;

import com.zurrtum.create.foundation.block.ResistanceControlBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;

/** A block of the mod: weak on its own, blast-proof where it stands reinforced. */
public class Casing extends Block implements ResistanceControlBlock {
	public Casing() {
		super(3.0F);
	}

	@Override
	public float getResistance(BlockGetter level, BlockPos pos) {
		return pos.name().equals("reinforced") ? 1200.0F : 3.0F;
	}
}
