package net.minecraft.world.item.enchantment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public record TargetedConditionalEffect<T>(EnchantmentTarget enchanted, EnchantmentTarget affected, T effect, Optional<LootItemCondition> requirements) {
   public static <S> Codec<TargetedConditionalEffect<S>> codec(final Codec<S> effectCodec, final ContextKeySet paramsSet) {
      return RecordCodecBuilder.create(
         i -> i.group(
               EnchantmentTarget.CODEC.fieldOf("enchanted").forGetter(TargetedConditionalEffect::enchanted),
               EnchantmentTarget.CODEC.fieldOf("affected").forGetter(TargetedConditionalEffect::affected),
               effectCodec.fieldOf("effect").forGetter(TargetedConditionalEffect::effect),
               ConditionalEffect.conditionCodec(paramsSet).optionalFieldOf("requirements").forGetter(TargetedConditionalEffect::requirements)
            )
            .apply(i, TargetedConditionalEffect::new)
      );
   }

   public static <S> Codec<TargetedConditionalEffect<S>> equipmentDropsCodec(final Codec<S> effectCodec, final ContextKeySet paramsSet) {
      return RecordCodecBuilder.create(
         i -> i.group(
               EnchantmentTarget.CODEC
                  .validate(
                     t -> t != EnchantmentTarget.DAMAGING_ENTITY ? DataResult.success(t) : DataResult.error(() -> "enchanted must be attacker or victim")
                  )
                  .fieldOf("enchanted")
                  .forGetter(TargetedConditionalEffect::enchanted),
               effectCodec.fieldOf("effect").forGetter(TargetedConditionalEffect::effect),
               ConditionalEffect.conditionCodec(paramsSet).optionalFieldOf("requirements").forGetter(TargetedConditionalEffect::requirements)
            )
            .apply(i, (target, effect, requirements) -> new TargetedConditionalEffect<>(target, EnchantmentTarget.VICTIM, effect, requirements))
      );
   }

   public boolean matches(final LootContext context) {
      return this.requirements.isEmpty() ? true : this.requirements.get().test(context);
   }
}
