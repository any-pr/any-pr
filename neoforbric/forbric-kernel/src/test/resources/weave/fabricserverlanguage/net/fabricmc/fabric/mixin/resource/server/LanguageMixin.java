package net.fabricmc.fabric.mixin.resource.server;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.locale.Language;

/**
 * Synthetic guest mixin in the shape of fabric-resource-loader's server language hook: the resource read of the
 * two-argument {@code parseTranslations} is redirected to the game's own copy of the file (here, a fixed one).
 */
@Mixin(Language.class)
abstract class LanguageMixin {
	@Redirect(method = "parseTranslations(Ljava/util/function/BiConsumer;Ljava/lang/String;)V",
			at = @At(value = "INVOKE", target = "Ljava/lang/Class;getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"))
	private static InputStream readCorrectVanillaResource(Class<?> owner, String path) {
		return new ByteArrayInputStream("greeting=from the game's own language file\n".getBytes(StandardCharsets.UTF_8));
	}
}
