package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jspecify.annotations.Nullable;

public class BredAnimalsTrigger extends SimpleCriterionTrigger<BredAnimalsTrigger.TriggerInstance> {
   @Override
   public Codec<BredAnimalsTrigger.TriggerInstance> codec() {
      return BredAnimalsTrigger.TriggerInstance.CODEC;
   }

   public void trigger(final ServerPlayer player, final Animal parent, final Animal partner, final @Nullable AgeableMob child) {
      LootContext parentContext = EntityPredicate.createContext(player, parent);
      LootContext partnerContext = EntityPredicate.createContext(player, partner);
      LootContext childContext = child != null ? EntityPredicate.createContext(player, child) : null;
      this.trigger(player, t -> t.matches(parentContext, partnerContext, childContext));
   }

   public record TriggerInstance(
      Optional<ContextAwarePredicate> player,
      Optional<ContextAwarePredicate> parent,
      Optional<ContextAwarePredicate> partner,
      Optional<ContextAwarePredicate> child
   ) implements SimpleCriterionTrigger.SimpleInstance {
      public static final Codec<BredAnimalsTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
         i -> i.group(
               EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(BredAnimalsTrigger.TriggerInstance::player),
               EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("parent").forGetter(BredAnimalsTrigger.TriggerInstance::parent),
               EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("partner").forGetter(BredAnimalsTrigger.TriggerInstance::partner),
               EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("child").forGetter(BredAnimalsTrigger.TriggerInstance::child)
            )
            .apply(i, BredAnimalsTrigger.TriggerInstance::new)
      );

      public static Criterion<BredAnimalsTrigger.TriggerInstance> bredAnimals() {
         return CriteriaTriggers.BRED_ANIMALS
            .createCriterion(new BredAnimalsTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
      }

      public static Criterion<BredAnimalsTrigger.TriggerInstance> bredAnimals(final EntityPredicate.Builder child) {
         return CriteriaTriggers.BRED_ANIMALS
            .createCriterion(
               new BredAnimalsTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(EntityPredicate.wrap(child)))
            );
      }

      public static Criterion<BredAnimalsTrigger.TriggerInstance> bredAnimals(
         final Optional<EntityPredicate> parent1, final Optional<EntityPredicate> parent2, final Optional<EntityPredicate> child
      ) {
         return CriteriaTriggers.BRED_ANIMALS
            .createCriterion(
               new BredAnimalsTrigger.TriggerInstance(
                  Optional.empty(), EntityPredicate.wrap(parent1), EntityPredicate.wrap(parent2), EntityPredicate.wrap(child)
               )
            );
      }

      public boolean matches(final LootContext parent, final LootContext partner, final @Nullable LootContext child) {
         return !this.child.isPresent() || child != null && this.child.get().matches(child)
            ? matches(this.parent, parent) && matches(this.partner, partner) || matches(this.parent, partner) && matches(this.partner, parent)
            : false;
      }

      private static boolean matches(final Optional<ContextAwarePredicate> predicate, final LootContext context) {
         return predicate.isEmpty() || predicate.get().matches(context);
      }

      @Override
      public void validate(final CriterionValidator validator) {
         SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
         validator.validateEntity(this.parent, "parent");
         validator.validateEntity(this.partner, "partner");
         validator.validateEntity(this.child, "child");
      }
   }
}
