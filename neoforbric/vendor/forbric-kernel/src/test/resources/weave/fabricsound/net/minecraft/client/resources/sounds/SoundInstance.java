package net.minecraft.client.resources.sounds;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sounds.SoundBufferLibrary;

/**
 * Hand-written stand-in, not game code: the merged sound instance with Fabric's interface injected, and NeoForge's
 * per-instance stream default in the shape {@code FabricSoundContractTransformer} leaves it — asking Fabric's
 * {@code getAudioStream} for the sound's path.
 */
public interface SoundInstance extends FabricSoundInstance {
	default CompletableFuture<?> getStream(SoundBufferLibrary library, Sound sound, boolean loop) {
		return ((FabricSoundInstance) this).getAudioStream(library, sound.getPath(), loop);
	}
}
