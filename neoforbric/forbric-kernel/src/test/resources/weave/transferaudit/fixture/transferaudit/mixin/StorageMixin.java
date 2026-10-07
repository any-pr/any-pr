package fixture.transferaudit.mixin;

import fixture.transferaudit.Trace;
import fixture.transferaudit.UnreviewedStorage;
import net.minecraftforge.energy.EnergyStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A MinecraftForge mod's mixin into transfer-critical code, which also carries a method under the name of the audit's
 * certificate: what it adds must not pass for a reviewed class.
 */
@Mixin({EnergyStorage.class, UnreviewedStorage.class})
public abstract class StorageMixin {
	@Inject(method = "receiveEnergy", at = @At("HEAD"))
	private void watchReceive(int maxReceive, boolean simulate, CallbackInfoReturnable<Integer> cir) {
		Trace.SEEN.add(getClass().getSimpleName() + " " + maxReceive);
	}

	private static void forbric$auditedTransferSnapshot() {
	}
}
