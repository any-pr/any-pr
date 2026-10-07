package fixture.createentitysound;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An entity steps onto stone, then onto the mod's running belt, then onto stone with the belt sounding under it: the
 * sounds the level played, in order, with their volume.
 */
public class Probe {
	public String probe() {
		Level level = new Level();
		Entity entity = new Entity(level);
		BlockState stone = new BlockState(new Block(new SoundType("stone")));
		BlockState belt = new BlockState(new Belt());
		entity.stepOn(new BlockPos("running"), stone);
		entity.stepOn(new BlockPos("running"), belt);
		entity.stepOnCombination(stone, belt, new BlockPos("running"));
		return String.join(", ", level.sounds);
	}
}
