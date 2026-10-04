package net.minecraft.world.entity.monster.piglin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class RememberIfHoglinWasKilled {
   public static BehaviorControl<LivingEntity> create() {
      return BehaviorBuilder.create(
         i -> i.group(i.present(MemoryModuleType.ATTACK_TARGET), i.registered(MemoryModuleType.HUNTED_RECENTLY))
            .apply(i, (attackTarget, huntedRecently) -> (level, body, timestamp) -> {
               LivingEntity target = i.get(attackTarget);
               if (target.getType() == EntityType.HOGLIN && target.isDeadOrDying()) {
                  huntedRecently.setWithExpiry(true, PiglinAi.TIME_BETWEEN_HUNTS.sample(body.level().random));
               }

               return true;
            })
      );
   }
}
