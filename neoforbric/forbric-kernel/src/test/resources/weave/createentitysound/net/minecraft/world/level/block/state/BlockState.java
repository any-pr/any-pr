package net.minecraft.world.level.block.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

/**
 * A stand-in: vanilla's getSoundType(), NeoForge's context-aware getSoundType(level, pos, entity), and NeoForge's
 * playStepSound, which hands the whole step to the block.
 */
public class BlockState {
	private final Block block;

	public BlockState(Block block) {
		this.block = block;
	}

	public Block getBlock() {
		return block;
	}

	public SoundType getSoundType() {
		return block.soundType();
	}

	public SoundType getSoundType(LevelReader level, BlockPos pos, Entity entity) {
		return block.soundType();
	}

	public void playStepSound(Level level, BlockPos pos, Entity entity, float volume, float pitch) {
		block.playStepSound(this, level, pos, entity, volume, pitch);
	}
}
