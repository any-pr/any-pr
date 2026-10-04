package net.minecraft.advancements.criterion;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public class CriterionValidator {
   private final ProblemReporter reporter;
   private final HolderGetter.Provider lootData;

   public CriterionValidator(final ProblemReporter reporter, final HolderGetter.Provider lootData) {
      this.reporter = reporter;
      this.lootData = lootData;
   }

   public void validateEntity(final Optional<ContextAwarePredicate> predicate, final String fieldName) {
      predicate.ifPresent(p -> this.validateEntity(p, fieldName));
   }

   public void validateEntities(final List<ContextAwarePredicate> predicates, final String fieldName) {
      this.validate(predicates, LootContextParamSets.ADVANCEMENT_ENTITY, fieldName);
   }

   public void validateEntity(final ContextAwarePredicate predicate, final String fieldName) {
      this.validate(predicate, LootContextParamSets.ADVANCEMENT_ENTITY, fieldName);
   }

   public void validate(final ContextAwarePredicate predicate, final ContextKeySet params, final String fieldName) {
      predicate.validate(new ValidationContext(this.reporter.forChild(new ProblemReporter.FieldPathElement(fieldName)), params, this.lootData));
   }

   public void validate(final List<ContextAwarePredicate> predicates, final ContextKeySet params, final String fieldName) {
      for (int i = 0; i < predicates.size(); i++) {
         ContextAwarePredicate predicate = predicates.get(i);
         predicate.validate(new ValidationContext(this.reporter.forChild(new ProblemReporter.IndexedFieldPathElement(fieldName, i)), params, this.lootData));
      }
   }
}
