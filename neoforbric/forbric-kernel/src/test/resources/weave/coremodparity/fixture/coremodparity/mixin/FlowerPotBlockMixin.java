package fixture.coremodparity.mixin;

import fixture.coremodparity.PotDescriber;
import fixture.coremodparity.Trace;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Fabric mod's flower-pot mixin written against vanilla: a hook anchored on the vanilla read of potted, and a method
 * of its own that reads the field through @Shadow.
 */
@Mixin(FlowerPotBlock.class)
public abstract class FlowerPotBlockMixin implements PotDescriber {
	@Shadow @Final private Block potted;

	@Inject(method = "getCloneItemStack", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/world/level/block/FlowerPotBlock;potted:Lnet/minecraft/world/level/block/Block;"))
	private void beforePlantRead(CallbackInfoReturnable<String> cir) {
		Trace.SEEN.add("anchor");
	}

	@Override
	public String describePot() {
		return "pot of " + potted.id();
	}
}
