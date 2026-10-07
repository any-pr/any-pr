package fixture.createentitysound;

import com.zurrtum.create.foundation.block.SoundControlBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

/** A block of the mod: metal on its own, a belt's sound where it runs. */
public class Belt extends Block implements SoundControlBlock {
	public Belt() {
		super(new SoundType("metal"));
	}

	@Override
	public SoundType getSoundGroup(LevelReader level, BlockPos pos) {
		return new SoundType(pos.name().equals("running") ? "belt" : "metal");
	}
}
