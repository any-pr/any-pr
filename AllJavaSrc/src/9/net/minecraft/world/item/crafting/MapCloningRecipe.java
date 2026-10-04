package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class MapCloningRecipe extends CustomRecipe {
   public MapCloningRecipe(final CraftingBookCategory category) {
      super(category);
   }

   public boolean matches(final CraftingInput input, final Level level) {
      if (input.ingredientCount() < 2) {
         return false;
      }

      boolean hasEmptyMaps = false;
      boolean hasSourceMap = false;

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (!itemStack.isEmpty()) {
            if (itemStack.has(DataComponents.MAP_ID)) {
               if (hasSourceMap) {
                  return false;
               }

               hasSourceMap = true;
            } else {
               if (!itemStack.is(Items.MAP)) {
                  return false;
               }

               hasEmptyMaps = true;
            }
         }
      }

      return hasSourceMap && hasEmptyMaps;
   }

   public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
      int count = 0;
      ItemStack source = ItemStack.EMPTY;

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (!itemStack.isEmpty()) {
            if (itemStack.has(DataComponents.MAP_ID)) {
               if (!source.isEmpty()) {
                  return ItemStack.EMPTY;
               }

               source = itemStack;
            } else {
               if (!itemStack.is(Items.MAP)) {
                  return ItemStack.EMPTY;
               }

               count++;
            }
         }
      }

      return !source.isEmpty() && count >= 1 ? source.copyWithCount(count + 1) : ItemStack.EMPTY;
   }

   @Override
   public RecipeSerializer<MapCloningRecipe> getSerializer() {
      return RecipeSerializer.MAP_CLONING;
   }
}
