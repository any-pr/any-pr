package net.minecraft.world.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A stand-in for the merged base's Entity: its three step-sound methods each hand the step to NeoForge's
 * BlockState.playStepSound, where vanilla asks state.getSoundType() and plays the sound itself.
 */
public class Entity {
	private final Level level;

	public Entity(Level level) {
		this.level = level;
	}

	public Level level() {
		return level;
	}

	protected void playStepSound(BlockPos pos, BlockState state) {
		state.playStepSound(this.level(), pos, this, 0.15F, 1.0F);
	}

	protected void playCombinationStepSounds(BlockState primary, BlockState secondary, BlockPos primaryPos, BlockPos secondaryPos) {
		primary.playStepSound(this.level(), primaryPos, this, 0.15F, 1.0F);
		this.playMuffledStepSound(secondary, secondaryPos);
	}

	protected void playMuffledStepSound(BlockState state, BlockPos pos) {
		state.playStepSound(this.level(), pos, this, 0.05F, 0.8F);
	}

	/** One step onto {@code state}, as movement makes it. */
	public void stepOn(BlockPos pos, BlockState state) {
		playStepSound(pos, state);
	}

	/** One step onto {@code primary} with {@code secondary} sounding under it (carpet on a block). */
	public void stepOnCombination(BlockState primary, BlockState secondary, BlockPos pos) {
		playCombinationStepSounds(primary, secondary, pos, pos);
	}
}
