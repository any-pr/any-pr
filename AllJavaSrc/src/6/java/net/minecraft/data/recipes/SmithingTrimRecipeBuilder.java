package net.minecraft.data.recipes;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.equipment.trim.TrimPattern;

public class SmithingTrimRecipeBuilder {
   private final RecipeCategory category;
   private final Ingredient template;
   private final Ingredient base;
   private final Ingredient addition;
   private final Holder<TrimPattern> pattern;
   private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();

   public SmithingTrimRecipeBuilder(
      final RecipeCategory category, final Ingredient template, final Ingredient base, final Ingredient addition, final Holder<TrimPattern> pattern
   ) {
      this.category = category;
      this.template = template;
      this.base = base;
      this.addition = addition;
      this.pattern = pattern;
   }

   public static SmithingTrimRecipeBuilder smithingTrim(
      final Ingredient template, final Ingredient base, final Ingredient addition, final Holder<TrimPattern> pattern, final RecipeCategory category
   ) {
      return new SmithingTrimRecipeBuilder(category, template, base, addition, pattern);
   }

   public SmithingTrimRecipeBuilder unlocks(final String name, final Criterion<?> criterion) {
      this.criteria.put(name, criterion);
      return this;
   }

   public void save(final RecipeOutput output, final ResourceKey<Recipe<?>> id) {
      this.ensureValid(id);
      Advancement.Builder advancement = output.advancement()
         .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
         .rewards(AdvancementRewards.Builder.recipe(id))
         .requirements(AdvancementRequirements.Strategy.OR);
      this.criteria.forEach(advancement::addCriterion);
      SmithingTrimRecipe recipe = new SmithingTrimRecipe(this.template, this.base, this.addition, this.pattern);
      output.accept(id, recipe, advancement.build(id.identifier().withPrefix("recipes/" + this.category.getFolderName() + "/")));
   }

   private void ensureValid(final ResourceKey<Recipe<?>> id) {
      if (this.criteria.isEmpty()) {
         throw new IllegalStateException("No way of obtaining recipe " + id.identifier());
      }
   }
}
