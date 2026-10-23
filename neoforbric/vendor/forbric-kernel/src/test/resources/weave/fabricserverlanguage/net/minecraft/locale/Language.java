package net.minecraft.locale;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

/**
 * Hand-written stand-in, not game code: the merged language reader. The two-argument form vanilla had is kept only as
 * a delegate; the form that opens the resource takes a second consumer, for the components NeoForge reads alongside.
 */
public abstract class Language {
	public static void parseTranslations(BiConsumer<String, String> output, String path) {
		parseTranslations(output, (key, component) -> { }, path);
	}

	public static void parseTranslations(BiConsumer<String, String> output, BiConsumer<String, String> components, String path) {
		try (InputStream in = Language.class.getResourceAsStream(path)) {
			if (in == null) {
				output.accept("missing", path);
				return;
			}
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				int split = line.indexOf('=');
				if (split > 0) output.accept(line.substring(0, split), line.substring(split + 1));
			}
		} catch (IOException unreadable) {
			output.accept("unreadable", path);
		}
	}
}
