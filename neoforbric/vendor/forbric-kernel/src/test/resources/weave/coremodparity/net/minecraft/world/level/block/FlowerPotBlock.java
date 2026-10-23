package net.minecraft.world.level.block;

import java.util.function.Supplier;

/**
 * Fixture stand-in for the merged flower pot: NeoForge's constructor stores null in vanilla's potted field and keeps
 * the plant in a supplier its getPotted() reads, while the vanilla-shaped body below still reads the field.
 */
public class FlowerPotBlock extends Block {
	private final Block potted;
	private final Supplier<? extends Block> plant;

	public FlowerPotBlock(Supplier<? extends Block> plant) {
		super("flower_pot");
		this.potted = null;
		this.plant = plant;
	}

	public Block getPotted() {
		return plant.get();
	}

	public String getCloneItemStack() {
		return "pick:" + potted.id();
	}
}
