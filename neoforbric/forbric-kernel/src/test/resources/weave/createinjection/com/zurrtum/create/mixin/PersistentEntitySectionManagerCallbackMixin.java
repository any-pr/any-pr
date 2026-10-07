package com.zurrtum.create.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import fixture.createinjection.Trail;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shaped like Create Fly's PersistentEntitySectionManagerCallbackMixin, written against vanilla's onMove: at
 * updateStatus it hands the mod the section the entity left (its parameter oldPos) through an implicit
 * {@code @Local long}, which is unambiguous only while one long is live there, as in vanilla.
 */
@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
public class PersistentEntitySectionManagerCallbackMixin {
	@Shadow @Final private EntityAccess entity;
	@Shadow private long currentSectionKey;

	@Inject(method = "onMove()V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager$Callback;updateStatus(Lnet/minecraft/world/level/entity/Visibility;Lnet/minecraft/world/level/entity/Visibility;)V"))
	private void onEnteringSection(CallbackInfo ci, @Local long oldPos) {
		Trail.add("create " + entity.name() + " " + oldPos + "->" + currentSectionKey);
	}
}
