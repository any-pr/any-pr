package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetStewEffectFunction extends LootItemConditionalFunction {
   private static final Codec<List<SetStewEffectFunction.EffectEntry>> EFFECTS_LIST = SetStewEffectFunction.EffectEntry.CODEC.listOf().validate(entries -> {
      Set<Holder<MobEffect>> seenEffects = new ObjectOpenHashSet();

      for (SetStewEffectFunction.EffectEntry entry : entries) {
         if (!seenEffects.add(entry.effect())) {
            return DataResult.error(() -> "Encountered duplicate mob effect: '" + entry.effect() + "'");
         }
      }

      return DataResult.success(entries);
   });
   public static final MapCodec<SetStewEffectFunction> CODEC = RecordCodecBuilder.mapCodec(
      i -> commonFields(i).and(EFFECTS_LIST.optionalFieldOf("effects", List.of()).forGetter(f -> f.effects)).apply(i, SetStewEffectFunction::new)
   );
   private final List<SetStewEffectFunction.EffectEntry> effects;

   private SetStewEffectFunction(final List<LootItemCondition> predicates, final List<SetStewEffectFunction.EffectEntry> effects) {
      super(predicates);
      this.effects = effects;
   }

   @Override
   public LootItemFunctionType<SetStewEffectFunction> getType() {
      return LootItemFunctions.SET_STEW_EFFECT;
   }

   @Override
   public Set<ContextKey<?>> getReferencedContextParams() {
      return this.effects.stream().flatMap(p -> p.duration().getReferencedContextParams().stream()).collect(ImmutableSet.toImmutableSet());
   }

   @Override
   public ItemStack run(final ItemStack itemStack, final LootContext context) {
      if (itemStack.is(Items.SUSPICIOUS_STEW) && !this.effects.isEmpty()) {
         SetStewEffectFunction.EffectEntry entry = Util.getRandom(this.effects, context.getRandom());
         Holder<MobEffect> effect = entry.effect();
         int duration = entry.duration().getInt(context);
         if (!effect.value().isInstantenous()) {
            duration *= 20;
         }

         SuspiciousStewEffects.Entry newEntry = new SuspiciousStewEffects.Entry(effect, duration);
         itemStack.update(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY, newEntry, SuspiciousStewEffects::withEffectAdded);
         return itemStack;
      } else {
         return itemStack;
      }
   }

   public static SetStewEffectFunction.Builder stewEffect() {
      return new SetStewEffectFunction.Builder();
   }

   public static class Builder extends LootItemConditionalFunction.Builder<SetStewEffectFunction.Builder> {
      private final com.google.common.collect.ImmutableList.Builder<SetStewEffectFunction.EffectEntry> effects = ImmutableList.builder();

      protected SetStewEffectFunction.Builder getThis() {
         return this;
      }

      public SetStewEffectFunction.Builder withEffect(final Holder<MobEffect> effect, final NumberProvider duration) {
         this.effects.add(new SetStewEffectFunction.EffectEntry(effect, duration));
         return this;
      }

      @Override
      public LootItemFunction build() {
         return new SetStewEffectFunction(this.getConditions(), this.effects.build());
      }
   }

   private record EffectEntry(Holder<MobEffect> effect, NumberProvider duration) {
      public static final Codec<SetStewEffectFunction.EffectEntry> CODEC = RecordCodecBuilder.create(
         i -> i.group(
               MobEffect.CODEC.fieldOf("type").forGetter(SetStewEffectFunction.EffectEntry::effect),
               NumberProviders.CODEC.fieldOf("duration").forGetter(SetStewEffectFunction.EffectEntry::duration)
            )
            .apply(i, SetStewEffectFunction.EffectEntry::new)
      );
   }
}
