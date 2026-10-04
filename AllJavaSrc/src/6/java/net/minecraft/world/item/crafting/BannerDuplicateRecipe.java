package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class BannerDuplicateRecipe extends CustomRecipe {
   public BannerDuplicateRecipe(final CraftingBookCategory category) {
      super(category);
   }

   public boolean matches(final CraftingInput input, final Level level) {
      if (input.ingredientCount() != 2) {
         return false;
      }

      DyeColor color = null;
      boolean hasTarget = false;
      boolean hasSource = false;

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (!itemStack.isEmpty()) {
            if (!(itemStack.getItem() instanceof BannerItem banner)) {
               return false;
            }

            if (color == null) {
               color = banner.getColor();
            } else if (color != banner.getColor()) {
               return false;
            }

            int patternCount = itemStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().size();
            if (patternCount > 6) {
               return false;
            }

            if (patternCount > 0) {
               if (hasSource) {
                  return false;
               }

               hasSource = true;
            } else {
               if (hasTarget) {
                  return false;
               }

               hasTarget = true;
            }
         }
      }

      return hasSource && hasTarget;
   }

   public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (!itemStack.isEmpty()) {
            int patternCount = itemStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().size();
            if (patternCount > 0 && patternCount <= 6) {
               return itemStack.copyWithCount(1);
            }
         }
      }

      return ItemStack.EMPTY;
   }

   @Override
   public NonNullList<ItemStack> getRemainingItems(final CraftingInput input) {
      NonNullList<ItemStack> result = NonNullList.withSize(input.size(), ItemStack.EMPTY);

      for (int slot = 0; slot < result.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (!itemStack.isEmpty()) {
            ItemStack remainder = itemStack.getItem().getCraftingRemainder();
            if (!remainder.isEmpty()) {
               result.set(slot, remainder);
            } else if (!itemStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().isEmpty()) {
               result.set(slot, itemStack.copyWithCount(1));
            }
         }
      }

      return result;
   }

   @Override
   public RecipeSerializer<BannerDuplicateRecipe> getSerializer() {
      return RecipeSerializer.BANNER_DUPLICATE;
   }
}
