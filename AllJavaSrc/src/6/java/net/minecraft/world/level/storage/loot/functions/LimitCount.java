package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class LimitCount extends LootItemConditionalFunction {
   public static final MapCodec<LimitCount> CODEC = RecordCodecBuilder.mapCodec(
      i -> commonFields(i).and(IntRange.CODEC.fieldOf("limit").forGetter(f -> f.limiter)).apply(i, LimitCount::new)
   );
   private final IntRange limiter;

   private LimitCount(final List<LootItemCondition> predicates, final IntRange limiter) {
      super(predicates);
      this.limiter = limiter;
   }

   @Override
   public LootItemFunctionType<LimitCount> getType() {
      return LootItemFunctions.LIMIT_COUNT;
   }

   @Override
   public Set<ContextKey<?>> getReferencedContextParams() {
      return this.limiter.getReferencedContextParams();
   }

   @Override
   public ItemStack run(final ItemStack itemStack, final LootContext context) {
      int limit = this.limiter.clamp(context, itemStack.getCount());
      itemStack.setCount(limit);
      return itemStack;
   }

   public static LootItemConditionalFunction.Builder<?> limitCount(final IntRange limiter) {
      return simpleBuilder(conditions -> new LimitCount(conditions, limiter));
   }
}
