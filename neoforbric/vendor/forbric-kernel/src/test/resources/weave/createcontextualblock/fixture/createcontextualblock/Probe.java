package fixture.createcontextualblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** How much a stone block and the mod's reinforced casing resist one explosion. */
public class Probe {
	public String probe() {
		ExplosionDamageCalculator calculator = new ExplosionDamageCalculator();
		BlockGetter level = new BlockGetter() {
		};
		Explosion explosion = new Explosion();
		return "stone " + calculator.getBlockExplosionResistance(explosion, level, new BlockPos("plain"),
				new BlockState(new Block(6.0F)), new FluidState()).orElseThrow()
				+ " | casing " + calculator.getBlockExplosionResistance(explosion, level, new BlockPos("reinforced"),
						new BlockState(new Casing()), new FluidState()).orElseThrow();
	}
}
