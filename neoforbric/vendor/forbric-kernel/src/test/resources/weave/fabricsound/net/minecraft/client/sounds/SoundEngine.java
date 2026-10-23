package net.minecraft.client.sounds;

import java.util.concurrent.CompletableFuture;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;

/** Hand-written stand-in, not game code: the merged engine asks the instance for its stream, never the library. */
public class SoundEngine {
	public enum PlayResult { STARTED, NOT_STARTED }

	private final SoundBufferLibrary library = new SoundBufferLibrary();
	public CompletableFuture<?> stream;

	public PlayResult play(SoundInstance sound) {
		stream = sound.getStream(library, new Sound(new Identifier("ambient/cave1")), false);
		return PlayResult.STARTED;
	}
}
