package net.minecraft.world.item.crafting;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;

public class FireworkStarFadeRecipe extends CustomRecipe {
   private static final Ingredient STAR_INGREDIENT = Ingredient.of(Items.FIREWORK_STAR);

   public FireworkStarFadeRecipe(final CraftingBookCategory category) {
      super(category);
   }

   public boolean matches(final CraftingInput input, final Level level) {
      if (input.ingredientCount() < 2) {
         return false;
      }

      boolean color = false;
      boolean star = false;

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (!itemStack.isEmpty()) {
            if (itemStack.getItem() instanceof DyeItem) {
               color = true;
            } else {
               if (!STAR_INGREDIENT.test(itemStack)) {
                  return false;
               }

               if (star) {
                  return false;
               }

               star = true;
            }
         }
      }

      return star && color;
   }

   public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
      IntList colors = new IntArrayList();
      ItemStack result = null;

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (itemStack.getItem() instanceof DyeItem dyeItem) {
            colors.add(dyeItem.getDyeColor().getFireworkColor());
         } else if (STAR_INGREDIENT.test(itemStack)) {
            result = itemStack.copyWithCount(1);
         }
      }

      if (result != null && !colors.isEmpty()) {
         result.update(DataComponents.FIREWORK_EXPLOSION, FireworkExplosion.DEFAULT, colors, FireworkExplosion::withFadeColors);
         return result;
      } else {
         return ItemStack.EMPTY;
      }
   }

   @Override
   public RecipeSerializer<FireworkStarFadeRecipe> getSerializer() {
      return RecipeSerializer.FIREWORK_STAR_FADE;
   }
}
