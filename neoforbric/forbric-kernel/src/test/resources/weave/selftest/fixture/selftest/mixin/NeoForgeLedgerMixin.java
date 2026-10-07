package fixture.selftest.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fixture.selftest.Ledger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Ledger.class)
public abstract class NeoForgeLedgerMixin {
	/** Appends its name; a mixin applied after it on the same return appends after it. */
	@ModifyReturnValue(method = "entries", at = @At("RETURN"))
	private String neoforge(String entries) {
		return entries + "|neoforge";
	}
}
