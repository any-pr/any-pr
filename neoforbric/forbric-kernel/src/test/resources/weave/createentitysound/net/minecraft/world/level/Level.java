package net.minecraft.world.level;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;

/** A stand-in that records each sound played: which, and how loud. */
public class Level implements LevelReader {
	public final List<String> sounds = new ArrayList<>();

	public void playSound(Entity entity, BlockPos pos, SoundType sound, float volume, float pitch) {
		sounds.add(sound.name() + " " + volume);
	}
}
