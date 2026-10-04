package net.minecraft.data.recipes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.TransmuteResult;

public class SmithingTransformRecipeBuilder {
   private final Ingredient template;
   private final Ingredient base;
   private final Ingredient addition;
   private final RecipeCategory category;
   private final Item result;
   private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();

   public SmithingTransformRecipeBuilder(
      final Ingredient template, final Ingredient base, final Ingredient addition, final RecipeCategory category, final Item result
   ) {
      this.category = category;
      this.template = template;
      this.base = base;
      this.addition = addition;
      this.result = result;
   }

   public static SmithingTransformRecipeBuilder smithing(
      final Ingredient template, final Ingredient base, final Ingredient addition, final RecipeCategory category, final Item result
   ) {
      return new SmithingTransformRecipeBuilder(template, base, addition, category, result);
   }

   public SmithingTransformRecipeBuilder unlocks(final String name, final Criterion<?> criterion) {
      this.criteria.put(name, criterion);
      return this;
   }

   public void save(final RecipeOutput output, final String id) {
      this.save(output, ResourceKey.create(Registries.RECIPE, Identifier.parse(id)));
   }

   public void save(final RecipeOutput output, final ResourceKey<Recipe<?>> id) {
      this.ensureValid(id);
      Advancement.Builder advancement = output.advancement()
         .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
         .rewards(AdvancementRewards.Builder.recipe(id))
         .requirements(AdvancementRequirements.Strategy.OR);
      this.criteria.forEach(advancement::addCriterion);
      SmithingTransformRecipe recipe = new SmithingTransformRecipe(
         Optional.of(this.template), this.base, Optional.of(this.addition), new TransmuteResult(this.result)
      );
      output.accept(id, recipe, advancement.build(id.identifier().withPrefix("recipes/" + this.category.getFolderName() + "/")));
   }

   private void ensureValid(final ResourceKey<Recipe<?>> id) {
      if (this.criteria.isEmpty()) {
         throw new IllegalStateException("No way of obtaining recipe " + id.identifier());
      }
   }
}
