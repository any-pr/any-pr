package net.fabricmc.fabric.mixin.client.sound;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;

/**
 * Synthetic guest mixin in the shape of fabric-sound-api's: vanilla's library stream call in {@code play} is
 * redirected to the instance's own {@code getAudioStream}. The handler is the one delegating call the adapter's
 * instruction fingerprint pins; nothing else of fabric-api's class is reproduced.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {
	@Redirect(method = "play", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/sounds/SoundBufferLibrary;getStream(Lnet/minecraft/resources/Identifier;Z)Ljava/util/concurrent/CompletableFuture;"))
	private CompletableFuture<?> getStream(SoundBufferLibrary library, Identifier path, boolean loop, SoundInstance sound) {
		return sound.getAudioStream(library, path, loop);
	}
}
