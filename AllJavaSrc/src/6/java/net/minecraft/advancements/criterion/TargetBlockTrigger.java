package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;

public class TargetBlockTrigger extends SimpleCriterionTrigger<TargetBlockTrigger.TriggerInstance> {
   @Override
   public Codec<TargetBlockTrigger.TriggerInstance> codec() {
      return TargetBlockTrigger.TriggerInstance.CODEC;
   }

   public void trigger(final ServerPlayer player, final Entity projectile, final Vec3 hitPosition, final int signalStrength) {
      LootContext projectileContext = EntityPredicate.createContext(player, projectile);
      this.trigger(player, t -> t.matches(projectileContext, hitPosition, signalStrength));
   }

   public record TriggerInstance(Optional<ContextAwarePredicate> player, MinMaxBounds.Ints signalStrength, Optional<ContextAwarePredicate> projectile)
      implements SimpleCriterionTrigger.SimpleInstance {
      public static final Codec<TargetBlockTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
         i -> i.group(
               EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TargetBlockTrigger.TriggerInstance::player),
               MinMaxBounds.Ints.CODEC.optionalFieldOf("signal_strength", MinMaxBounds.Ints.ANY).forGetter(TargetBlockTrigger.TriggerInstance::signalStrength),
               EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("projectile").forGetter(TargetBlockTrigger.TriggerInstance::projectile)
            )
            .apply(i, TargetBlockTrigger.TriggerInstance::new)
      );

      public static Criterion<TargetBlockTrigger.TriggerInstance> targetHit(
         final MinMaxBounds.Ints redstoneSignalStrength, final Optional<ContextAwarePredicate> projectile
      ) {
         return CriteriaTriggers.TARGET_BLOCK_HIT.createCriterion(new TargetBlockTrigger.TriggerInstance(Optional.empty(), redstoneSignalStrength, projectile));
      }

      public boolean matches(final LootContext projectile, final Vec3 hitPosition, final int signalStrength) {
         return !this.signalStrength.matches(signalStrength) ? false : !this.projectile.isPresent() || this.projectile.get().matches(projectile);
      }

      @Override
      public void validate(final CriterionValidator validator) {
         SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
         validator.validateEntity(this.projectile, "projectile");
      }
   }
}
