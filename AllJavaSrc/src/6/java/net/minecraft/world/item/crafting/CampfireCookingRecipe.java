package net.minecraft.world.item.crafting;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CampfireCookingRecipe extends AbstractCookingRecipe {
   public CampfireCookingRecipe(
      final String group,
      final CookingBookCategory category,
      final Ingredient ingredient,
      final ItemStack result,
      final float experience,
      final int cookingTime
   ) {
      super(group, category, ingredient, result, experience, cookingTime);
   }

   @Override
   protected Item furnaceIcon() {
      return Items.CAMPFIRE;
   }

   @Override
   public RecipeSerializer<CampfireCookingRecipe> getSerializer() {
      return RecipeSerializer.CAMPFIRE_COOKING_RECIPE;
   }

   @Override
   public RecipeType<CampfireCookingRecipe> getType() {
      return RecipeType.CAMPFIRE_COOKING;
   }

   @Override
   public RecipeBookCategory recipeBookCategory() {
      return RecipeBookCategories.CAMPFIRE;
   }
}
