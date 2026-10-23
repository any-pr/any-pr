package fixture.atshape.mixin;

import fixture.atshape.Spawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Written exactly as a mod writes a @Redirect for the Mixin it ships with — but compiled against the fork's
 * {@code Redirect} next to this fixture, whose {@code at} is {@code At[]}, so the class file carries
 * {@code at=[@At(...)]} the way architectury-fabric's MixinNaturalSpawner does.
 */
@Mixin(Spawner.class)
public abstract class SpawnerMixin {
	@Redirect(method = "spawn", at = @At(value = "INVOKE", target = "Lfixture/atshape/Spawner;limit()I"))
	private int raiseLimit(Spawner self) {
		return 64;
	}
}
