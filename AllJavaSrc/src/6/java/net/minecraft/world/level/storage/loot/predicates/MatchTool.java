package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public record MatchTool(Optional<ItemPredicate> predicate) implements LootItemCondition {
   public static final MapCodec<MatchTool> CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(ItemPredicate.CODEC.optionalFieldOf("predicate").forGetter(MatchTool::predicate)).apply(i, MatchTool::new)
   );

   @Override
   public LootItemConditionType getType() {
      return LootItemConditions.MATCH_TOOL;
   }

   @Override
   public Set<ContextKey<?>> getReferencedContextParams() {
      return Set.of(LootContextParams.TOOL);
   }

   public boolean test(final LootContext context) {
      ItemStack tool = context.getOptionalParameter(LootContextParams.TOOL);
      return tool != null && (this.predicate.isEmpty() || this.predicate.get().test(tool));
   }

   public static LootItemCondition.Builder toolMatches(final ItemPredicate.Builder predicate) {
      return () -> new MatchTool(Optional.of(predicate.build()));
   }
}
