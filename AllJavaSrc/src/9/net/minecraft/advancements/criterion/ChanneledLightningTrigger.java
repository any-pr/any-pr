package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;

public class ChanneledLightningTrigger extends SimpleCriterionTrigger<ChanneledLightningTrigger.TriggerInstance> {
   @Override
   public Codec<ChanneledLightningTrigger.TriggerInstance> codec() {
      return ChanneledLightningTrigger.TriggerInstance.CODEC;
   }

   public void trigger(final ServerPlayer player, final Collection<? extends Entity> victims) {
      List<LootContext> victimsContexts = victims.stream().map(v -> EntityPredicate.createContext(player, v)).collect(Collectors.toList());
      this.trigger(player, t -> t.matches(victimsContexts));
   }

   public record TriggerInstance(Optional<ContextAwarePredicate> player, List<ContextAwarePredicate> victims) implements SimpleCriterionTrigger.SimpleInstance {
      public static final Codec<ChanneledLightningTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
         i -> i.group(
               EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(ChanneledLightningTrigger.TriggerInstance::player),
               EntityPredicate.ADVANCEMENT_CODEC.listOf().optionalFieldOf("victims", List.of()).forGetter(ChanneledLightningTrigger.TriggerInstance::victims)
            )
            .apply(i, ChanneledLightningTrigger.TriggerInstance::new)
      );

      public static Criterion<ChanneledLightningTrigger.TriggerInstance> channeledLightning(final EntityPredicate.Builder... victims) {
         return CriteriaTriggers.CHANNELED_LIGHTNING
            .createCriterion(new ChanneledLightningTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(victims)));
      }

      public boolean matches(final Collection<? extends LootContext> victims) {
         for (ContextAwarePredicate predicate : this.victims) {
            boolean found = false;

            for (LootContext victim : victims) {
               if (predicate.matches(victim)) {
                  found = true;
                  break;
               }
            }

            if (!found) {
               return false;
            }
         }

         return true;
      }

      @Override
      public void validate(final CriterionValidator validator) {
         SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
         validator.validateEntities(this.victims, "victims");
      }
   }
}
