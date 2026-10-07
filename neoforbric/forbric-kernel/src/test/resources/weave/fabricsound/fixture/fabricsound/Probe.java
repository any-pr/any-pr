package fixture.fabricsound;

import java.util.concurrent.CompletableFuture;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;

/**
 * Plays a sound that supplies its own stream, and reports the stream and which method of the engine asked for it:
 * {@code play} itself, or a handler Mixin merged into the engine.
 */
public class Probe {
	public String run() throws Exception {
		CustomSound sound = new CustomSound();
		SoundEngine engine = new SoundEngine();
		SoundEngine.PlayResult result = engine.play(sound);
		return result + " " + engine.stream.get() + " asked-by=" + sound.askedBy;
	}

	static final class CustomSound implements SoundInstance {
		String askedBy = "nobody";

		@Override
		public CompletableFuture<?> getAudioStream(SoundBufferLibrary library, Identifier path, boolean loop) {
			// Frame 0 is this method, frame 1 the instance's getStream default, frame 2 whoever called that.
			StackWalker.StackFrame caller = StackWalker.getInstance().walk(frames -> frames.skip(2).findFirst()).orElseThrow();
			askedBy = !caller.getClassName().equals(SoundEngine.class.getName()) ? caller.getClassName()
					: caller.getMethodName().equals("play") ? "play" : caller.getMethodName().endsWith("getStream") ? "merged-handler" : caller.getMethodName();
			return CompletableFuture.completedFuture("custom:" + path.path());
		}
	}
}
