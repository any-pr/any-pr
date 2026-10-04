package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.Products.P1;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public abstract class LootPoolEntryContainer implements ComposableEntryContainer {
   protected final List<LootItemCondition> conditions;
   private final Predicate<LootContext> compositeCondition;

   protected LootPoolEntryContainer(final List<LootItemCondition> conditions) {
      this.conditions = conditions;
      this.compositeCondition = Util.allOf(conditions);
   }

   protected static <T extends LootPoolEntryContainer> P1<Mu<T>, List<LootItemCondition>> commonFields(final Instance<T> i) {
      return i.group(LootItemCondition.DIRECT_CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(e -> e.conditions));
   }

   public void validate(final ValidationContext output) {
      for (int i = 0; i < this.conditions.size(); i++) {
         this.conditions.get(i).validate(output.forChild(new ProblemReporter.IndexedFieldPathElement("conditions", i)));
      }
   }

   protected final boolean canRun(final LootContext context) {
      return this.compositeCondition.test(context);
   }

   public abstract LootPoolEntryType getType();

   public abstract static class Builder<T extends LootPoolEntryContainer.Builder<T>> implements ConditionUserBuilder<T> {
      private final com.google.common.collect.ImmutableList.Builder<LootItemCondition> conditions = ImmutableList.builder();

      protected abstract T getThis();

      public T when(final LootItemCondition.Builder condition) {
         this.conditions.add(condition.build());
         return this.getThis();
      }

      public final T unwrap() {
         return this.getThis();
      }

      protected List<LootItemCondition> getConditions() {
         return this.conditions.build();
      }

      public AlternativesEntry.Builder otherwise(final LootPoolEntryContainer.Builder<?> other) {
         return new AlternativesEntry.Builder(this, other);
      }

      public EntryGroup.Builder append(final LootPoolEntryContainer.Builder<?> other) {
         return new EntryGroup.Builder(this, other);
      }

      public SequentialEntry.Builder then(final LootPoolEntryContainer.Builder<?> other) {
         return new SequentialEntry.Builder(this, other);
      }

      public abstract LootPoolEntryContainer build();
   }
}
