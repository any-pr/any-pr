package net.minecraft.world.level.block;

import net.neoforged.neoforge.common.extensions.IBlockExtension;

/** A stand-in: a block and its own sound group. */
public class Block implements IBlockExtension {
	private final SoundType soundType;

	public Block(SoundType soundType) {
		this.soundType = soundType;
	}

	public SoundType soundType() {
		return soundType;
	}
}
