package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;

public record InvertedLootItemCondition(LootItemCondition term) implements LootItemCondition {
   public static final MapCodec<InvertedLootItemCondition> CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(LootItemCondition.DIRECT_CODEC.fieldOf("term").forGetter(InvertedLootItemCondition::term)).apply(i, InvertedLootItemCondition::new)
   );

   @Override
   public LootItemConditionType getType() {
      return LootItemConditions.INVERTED;
   }

   public boolean test(final LootContext context) {
      return !this.term.test(context);
   }

   @Override
   public Set<ContextKey<?>> getReferencedContextParams() {
      return this.term.getReferencedContextParams();
   }

   @Override
   public void validate(final ValidationContext output) {
      LootItemCondition.super.validate(output);
      this.term.validate(output);
   }

   public static LootItemCondition.Builder invert(final LootItemCondition.Builder term) {
      InvertedLootItemCondition result = new InvertedLootItemCondition(term.build());
      return () -> result;
   }
}
