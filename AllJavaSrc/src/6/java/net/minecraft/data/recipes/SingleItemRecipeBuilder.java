package net.minecraft.data.recipes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

public class SingleItemRecipeBuilder implements RecipeBuilder {
   private final RecipeCategory category;
   private final Item result;
   private final Ingredient ingredient;
   private final int count;
   private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
   private @Nullable String group;
   private final SingleItemRecipe.Factory<?> factory;

   public SingleItemRecipeBuilder(
      final RecipeCategory category, final SingleItemRecipe.Factory<?> factory, final Ingredient ingredient, final ItemLike result, final int count
   ) {
      this.category = category;
      this.factory = factory;
      this.result = result.asItem();
      this.ingredient = ingredient;
      this.count = count;
   }

   public static SingleItemRecipeBuilder stonecutting(final Ingredient ingredient, final RecipeCategory category, final ItemLike result) {
      return new SingleItemRecipeBuilder(category, StonecutterRecipe::new, ingredient, result, 1);
   }

   public static SingleItemRecipeBuilder stonecutting(final Ingredient ingredient, final RecipeCategory category, final ItemLike result, final int count) {
      return new SingleItemRecipeBuilder(category, StonecutterRecipe::new, ingredient, result, count);
   }

   public SingleItemRecipeBuilder unlockedBy(final String name, final Criterion<?> criterion) {
      this.criteria.put(name, criterion);
      return this;
   }

   public SingleItemRecipeBuilder group(final @Nullable String group) {
      this.group = group;
      return this;
   }

   @Override
   public Item getResult() {
      return this.result;
   }

   @Override
   public void save(final RecipeOutput output, final ResourceKey<Recipe<?>> id) {
      this.ensureValid(id);
      Advancement.Builder advancement = output.advancement()
         .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
         .rewards(AdvancementRewards.Builder.recipe(id))
         .requirements(AdvancementRequirements.Strategy.OR);
      this.criteria.forEach(advancement::addCriterion);
      SingleItemRecipe recipe = this.factory.create(Objects.requireNonNullElse(this.group, ""), this.ingredient, new ItemStack(this.result, this.count));
      output.accept(id, recipe, advancement.build(id.identifier().withPrefix("recipes/" + this.category.getFolderName() + "/")));
   }

   private void ensureValid(final ResourceKey<Recipe<?>> id) {
      if (this.criteria.isEmpty()) {
         throw new IllegalStateException("No way of obtaining recipe " + id.identifier());
      }
   }
}
