package fixture.retypedfield.mixin;

import java.util.List;

import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A guest mixin compiled against vanilla, where the field is a Monster and tick's second look is Monster.lookAt
 * (debugify's MC-121706 fix has this shape: it re-aims the look control right after it).
 */
@Mixin(RangedBowAttackGoal.class)
public abstract class RangedBowAttackGoalMixin {
	@Shadow @Final private List<String> trace;

	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Monster;lookAt(Lnet/minecraft/world/entity/Entity;FF)V", shift = At.Shift.AFTER))
	private void lookAtTarget(CallbackInfoReturnable<String> cir) {
		trace.add("fix");
	}
}
