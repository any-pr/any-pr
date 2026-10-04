package net.minecraft.world.item.enchantment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public record ConditionalEffect<T>(T effect, Optional<LootItemCondition> requirements) {
   public static Codec<LootItemCondition> conditionCodec(final ContextKeySet paramsSet) {
      return LootItemCondition.DIRECT_CODEC
         .validate(
            condition -> {
               ProblemReporter.Collector problemCollector = new ProblemReporter.Collector();
               ValidationContext validationContext = new ValidationContext(problemCollector, paramsSet);
               condition.validate(validationContext);
               return !problemCollector.isEmpty()
                  ? DataResult.error(() -> "Validation error in enchantment effect condition: " + problemCollector.getReport())
                  : DataResult.success(condition);
            }
         );
   }

   public static <T> Codec<ConditionalEffect<T>> codec(final Codec<T> effectCodec, final ContextKeySet paramsSet) {
      return RecordCodecBuilder.create(
         i -> i.group(
               effectCodec.fieldOf("effect").forGetter(ConditionalEffect::effect),
               conditionCodec(paramsSet).optionalFieldOf("requirements").forGetter(ConditionalEffect::requirements)
            )
            .apply(i, ConditionalEffect::new)
      );
   }

   public boolean matches(final LootContext context) {
      return this.requirements.isEmpty() ? true : this.requirements.get().test(context);
   }
}
