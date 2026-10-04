package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class TippedArrowRecipe extends CustomRecipe {
   public TippedArrowRecipe(final CraftingBookCategory category) {
      super(category);
   }

   public boolean matches(final CraftingInput input, final Level level) {
      if (input.width() == 3 && input.height() == 3 && input.ingredientCount() == 9) {
         for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
               ItemStack ingredient = input.getItem(x, y);
               if (ingredient.isEmpty()) {
                  return false;
               }

               if (x == 1 && y == 1) {
                  if (!ingredient.is(Items.LINGERING_POTION)) {
                     return false;
                  }
               } else if (!ingredient.is(Items.ARROW)) {
                  return false;
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
      ItemStack potion = input.getItem(1, 1);
      if (!potion.is(Items.LINGERING_POTION)) {
         return ItemStack.EMPTY;
      }

      ItemStack result = new ItemStack(Items.TIPPED_ARROW, 8);
      result.set(DataComponents.POTION_CONTENTS, potion.get(DataComponents.POTION_CONTENTS));
      return result;
   }

   @Override
   public RecipeSerializer<TippedArrowRecipe> getSerializer() {
      return RecipeSerializer.TIPPED_ARROW;
   }
}
