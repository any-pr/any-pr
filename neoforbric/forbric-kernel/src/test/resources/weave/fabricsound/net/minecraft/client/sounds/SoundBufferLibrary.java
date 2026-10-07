package net.minecraft.client.sounds;

import java.util.concurrent.CompletableFuture;

import net.minecraft.resources.Identifier;

/** Hand-written stand-in, not game code: opens a sound file as a stream. */
public class SoundBufferLibrary {
	public CompletableFuture<String> getStream(Identifier path, boolean loop) {
		return CompletableFuture.completedFuture("library:" + path.path());
	}
}
