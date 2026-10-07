package net.fabricmc.fabric.api.client.sound.v1;

import java.util.concurrent.CompletableFuture;

import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;

/** Hand-written stand-in for fabric-sound-api's interface: a sound may supply its own audio stream. */
public interface FabricSoundInstance {
	default CompletableFuture<?> getAudioStream(SoundBufferLibrary library, Identifier path, boolean loop) {
		return library.getStream(path, loop);
	}
}
