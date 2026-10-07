package fixture.fabricfreezehook.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fixture.fabricfreezehook.Create;
import fixture.fabricfreezehook.CreateRegistries;
import fixture.fabricfreezehook.Trace;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Synthetic guest mixin in the shape of Create Fly's {@code BuiltInRegistriesMixin}: at HEAD of the freeze it creates
 * the registries itself unless fabric-api is loaded, and at TAIL it reads them, as {@code ArmInteractionPointType}
 * does — the first touch of {@link CreateRegistries} when fabric-api's main has not run yet.
 */
@Mixin(BuiltInRegistries.class)
public class BuiltInRegistriesMixin {
	@Inject(method = "freeze()V", at = @At("HEAD"))
	private static void onInitialize(CallbackInfo info) {
		Trace.add("create:head");
		if (!Create.fabricApiLoaded()) Create.register();
	}

	@Inject(method = "freeze()V", at = @At("TAIL"))
	private static void afterFreeze(CallbackInfo info) {
		Trace.add("create:tail");
		CreateRegistries.ARM_INTERACTION_POINT_TYPE.register("create:depot");
	}
}
