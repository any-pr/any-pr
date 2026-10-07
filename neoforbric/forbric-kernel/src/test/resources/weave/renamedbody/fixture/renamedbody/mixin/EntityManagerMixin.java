package fixture.renamedbody.mixin;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Written the way carpet and architectury write their entity-add hooks against vanilla, where addEntity carries the
 * body. The HEAD handler binds where it is, so the mixin reads PARTIAL rather than UNFIT, as a real one's others do.
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class EntityManagerMixin {
	@Shadow @Final public StringBuilder trace;

	@Inject(method = "addEntity", at = @At("HEAD"))
	private void renamedbody$head(EntityAccess entity, boolean worldGenSpawned, CallbackInfoReturnable<Boolean> cir) {
		trace.append("head;");
	}

	/** An anchor in vanilla's body, which NeoForge renamed: the handler takes addEntity's own arguments. */
	@Inject(method = "addEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/Visibility;isTicking()Z"))
	private void renamedbody$added(EntityAccess entity, boolean worldGenSpawned, CallbackInfoReturnable<Boolean> cir) {
		trace.append("added;");
	}
}
