package net.minecraft.data.recipes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

public class ShapelessRecipeBuilder implements RecipeBuilder {
   private final HolderGetter<Item> items;
   private final RecipeCategory category;
   private final ItemStack result;
   private final List<Ingredient> ingredients = new ArrayList<>();
   private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
   private @Nullable String group;

   private ShapelessRecipeBuilder(final HolderGetter<Item> items, final RecipeCategory category, final ItemStack result) {
      this.items = items;
      this.category = category;
      this.result = result;
   }

   public static ShapelessRecipeBuilder shapeless(final HolderGetter<Item> items, final RecipeCategory category, final ItemStack result) {
      return new ShapelessRecipeBuilder(items, category, result);
   }

   public static ShapelessRecipeBuilder shapeless(final HolderGetter<Item> items, final RecipeCategory category, final ItemLike item) {
      return shapeless(items, category, item, 1);
   }

   public static ShapelessRecipeBuilder shapeless(final HolderGetter<Item> items, final RecipeCategory category, final ItemLike item, final int count) {
      return new ShapelessRecipeBuilder(items, category, item.asItem().getDefaultInstance().copyWithCount(count));
   }

   public ShapelessRecipeBuilder requires(final TagKey<Item> tag) {
      return this.requires(Ingredient.of(this.items.getOrThrow(tag)));
   }

   public ShapelessRecipeBuilder requires(final ItemLike item) {
      return this.requires(item, 1);
   }

   public ShapelessRecipeBuilder requires(final ItemLike item, final int count) {
      for (int i = 0; i < count; i++) {
         this.requires(Ingredient.of(item));
      }

      return this;
   }

   public ShapelessRecipeBuilder requires(final Ingredient ingredient) {
      return this.requires(ingredient, 1);
   }

   public ShapelessRecipeBuilder requires(final Ingredient ingredient, final int count) {
      for (int i = 0; i < count; i++) {
         this.ingredients.add(ingredient);
      }

      return this;
   }

   public ShapelessRecipeBuilder unlockedBy(final String name, final Criterion<?> criterion) {
      this.criteria.put(name, criterion);
      return this;
   }

   public ShapelessRecipeBuilder group(final @Nullable String group) {
      this.group = group;
      return this;
   }

   @Override
   public Item getResult() {
      return this.result.getItem();
   }

   @Override
   public void save(final RecipeOutput output, final ResourceKey<Recipe<?>> id) {
      this.ensureValid(id);
      Advancement.Builder advancement = output.advancement()
         .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
         .rewards(AdvancementRewards.Builder.recipe(id))
         .requirements(AdvancementRequirements.Strategy.OR);
      this.criteria.forEach(advancement::addCriterion);
      ShapelessRecipe recipe = new ShapelessRecipe(
         Objects.requireNonNullElse(this.group, ""), RecipeBuilder.determineBookCategory(this.category), this.result, this.ingredients
      );
      output.accept(id, recipe, advancement.build(id.identifier().withPrefix("recipes/" + this.category.getFolderName() + "/")));
   }

   private void ensureValid(final ResourceKey<Recipe<?>> id) {
      if (this.criteria.isEmpty()) {
         throw new IllegalStateException("No way of obtaining recipe " + id.identifier());
      }
   }
}
