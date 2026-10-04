package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.server.PlayerAdvancements;

public class ImpossibleTrigger implements CriterionTrigger<ImpossibleTrigger.TriggerInstance> {
   @Override
   public void addPlayerListener(final PlayerAdvancements player, final CriterionTrigger.Listener<ImpossibleTrigger.TriggerInstance> listener) {
   }

   @Override
   public void removePlayerListener(final PlayerAdvancements player, final CriterionTrigger.Listener<ImpossibleTrigger.TriggerInstance> listener) {
   }

   @Override
   public void removePlayerListeners(final PlayerAdvancements player) {
   }

   @Override
   public Codec<ImpossibleTrigger.TriggerInstance> codec() {
      return ImpossibleTrigger.TriggerInstance.CODEC;
   }

   public record TriggerInstance() implements CriterionTriggerInstance {
      public static final Codec<ImpossibleTrigger.TriggerInstance> CODEC = MapCodec.unitCodec(new ImpossibleTrigger.TriggerInstance());

      @Override
      public void validate(final CriterionValidator validator) {
      }
   }
}
