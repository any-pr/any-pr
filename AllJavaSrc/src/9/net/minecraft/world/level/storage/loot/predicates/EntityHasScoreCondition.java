package net.minecraft.world.level.storage.loot.predicates;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

public record EntityHasScoreCondition(Map<String, IntRange> scores, LootContext.EntityTarget entityTarget) implements LootItemCondition {
   public static final MapCodec<EntityHasScoreCondition> CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(
            Codec.unboundedMap(Codec.STRING, IntRange.CODEC).fieldOf("scores").forGetter(EntityHasScoreCondition::scores),
            LootContext.EntityTarget.CODEC.fieldOf("entity").forGetter(EntityHasScoreCondition::entityTarget)
         )
         .apply(i, EntityHasScoreCondition::new)
   );

   @Override
   public LootItemConditionType getType() {
      return LootItemConditions.ENTITY_SCORES;
   }

   @Override
   public Set<ContextKey<?>> getReferencedContextParams() {
      return Stream.concat(Stream.of(this.entityTarget.contextParam()), this.scores.values().stream().flatMap(r -> r.getReferencedContextParams().stream()))
         .collect(ImmutableSet.toImmutableSet());
   }

   public boolean test(final LootContext context) {
      Entity entity = context.getOptionalParameter(this.entityTarget.contextParam());
      if (entity == null) {
         return false;
      }

      Scoreboard scoreboard = context.getLevel().getScoreboard();

      for (Entry<String, IntRange> entry : this.scores.entrySet()) {
         if (!this.hasScore(context, entity, scoreboard, entry.getKey(), entry.getValue())) {
            return false;
         }
      }

      return true;
   }

   protected boolean hasScore(final LootContext context, final Entity entity, final Scoreboard scoreboard, final String objectiveName, final IntRange range) {
      Objective objective = scoreboard.getObjective(objectiveName);
      if (objective == null) {
         return false;
      }

      ReadOnlyScoreInfo scoreInfo = scoreboard.getPlayerScoreInfo(entity, objective);
      return scoreInfo == null ? false : range.test(context, scoreInfo.value());
   }

   public static EntityHasScoreCondition.Builder hasScores(final LootContext.EntityTarget target) {
      return new EntityHasScoreCondition.Builder(target);
   }

   public static class Builder implements LootItemCondition.Builder {
      private final com.google.common.collect.ImmutableMap.Builder<String, IntRange> scores = ImmutableMap.builder();
      private final LootContext.EntityTarget entityTarget;

      public Builder(final LootContext.EntityTarget entityTarget) {
         this.entityTarget = entityTarget;
      }

      public EntityHasScoreCondition.Builder withScore(final String score, final IntRange bounds) {
         this.scores.put(score, bounds);
         return this;
      }

      @Override
      public LootItemCondition build() {
         return new EntityHasScoreCondition(this.scores.build(), this.entityTarget);
      }
   }
}
