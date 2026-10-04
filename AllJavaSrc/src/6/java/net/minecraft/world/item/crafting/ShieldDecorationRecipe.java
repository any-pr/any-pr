package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class ShieldDecorationRecipe extends CustomRecipe {
   public ShieldDecorationRecipe(final CraftingBookCategory category) {
      super(category);
   }

   public boolean matches(final CraftingInput input, final Level level) {
      if (input.ingredientCount() != 2) {
         return false;
      }

      boolean hasClearShield = false;
      boolean hasPatternBanner = false;

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (!itemStack.isEmpty()) {
            if (itemStack.getItem() instanceof BannerItem) {
               if (hasPatternBanner) {
                  return false;
               }

               hasPatternBanner = true;
            } else {
               if (!itemStack.is(Items.SHIELD)) {
                  return false;
               }

               if (hasClearShield) {
                  return false;
               }

               BannerPatternLayers patterns = itemStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
               if (!patterns.layers().isEmpty()) {
                  return false;
               }

               hasClearShield = true;
            }
         }
      }

      return hasClearShield && hasPatternBanner;
   }

   public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
      ItemStack patternBanner = ItemStack.EMPTY;
      ItemStack shield = ItemStack.EMPTY;

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (!itemStack.isEmpty()) {
            if (itemStack.getItem() instanceof BannerItem) {
               patternBanner = itemStack;
            } else if (itemStack.is(Items.SHIELD)) {
               shield = itemStack.copy();
            }
         }
      }

      if (shield.isEmpty()) {
         return shield;
      }

      shield.set(DataComponents.BANNER_PATTERNS, patternBanner.get(DataComponents.BANNER_PATTERNS));
      shield.set(DataComponents.BASE_COLOR, ((BannerItem)patternBanner.getItem()).getColor());
      return shield;
   }

   @Override
   public RecipeSerializer<ShieldDecorationRecipe> getSerializer() {
      return RecipeSerializer.SHIELD_DECORATION;
   }
}
