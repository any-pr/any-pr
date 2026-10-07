package fixture.widenedredirect.mixin;

import java.util.function.Function;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.widenedredirect.ConfigurationListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A guest mixin compiled against vanilla, where each call is the one-argument form (creativecore's configuration
 * listener mixins redirect the decorator call this way, require=1, to build their own buffer).
 */
@Mixin(ConfigurationListener.class)
public abstract class ConfigurationListenerMixin {
	private static final String DECORATOR = "Lnet/minecraft/network/RegistryFriendlyByteBuf;decorator(Lnet/minecraft/core/RegistryAccess;)Ljava/util/function/Function;";

	@Redirect(method = "finish", at = @At(value = "INVOKE", target = DECORATOR))
	private Function<String, String> mine(RegistryAccess registries) {
		return payload -> "mine(" + registries.name() + ":" + payload + ")";
	}

	@Redirect(method = "finishCaptured", at = @At(value = "INVOKE", target = DECORATOR))
	private Function<String, String> mineCaptured(RegistryAccess registries, String packet) {
		return payload -> "mine(" + registries.name() + ":" + payload + "|" + packet + ")";
	}

	@Redirect(method = "encoded", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/RegistryFriendlyByteBuf;codec(Ljava/lang/String;)Ljava/lang/String;"), require = 0)
	private String mineCodec(String payload) {
		return "mine(" + payload + ")";
	}

	@Redirect(method = "wrapped", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/RegistryFriendlyByteBuf;wrap(Ljava/lang/String;)Ljava/lang/String;"), require = 0)
	private String mineWrapped(RegistryFriendlyByteBuf buffer, String payload) {
		return "mine(" + payload + ")";
	}
}
