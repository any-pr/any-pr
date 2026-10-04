package net.minecraft.world.item.crafting;

import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class MapExtendingRecipe extends ShapedRecipe {
   public MapExtendingRecipe(final CraftingBookCategory category) {
      super(
         "",
         category,
         ShapedRecipePattern.of(Map.of('#', Ingredient.of(Items.PAPER), 'x', Ingredient.of(Items.FILLED_MAP)), "###", "#x#", "###"),
         new ItemStack(Items.MAP)
      );
   }

   @Override
   public boolean matches(final CraftingInput input, final Level level) {
      if (!super.matches(input, level)) {
         return false;
      } else {
         ItemStack map = findFilledMap(input);
         if (map.isEmpty()) {
            return false;
         } else {
            MapItemSavedData data = MapItem.getSavedData(map, level);
            if (data == null) {
               return false;
            } else {
               return data.isExplorationMap() ? false : data.scale < 4;
            }
         }
      }
   }

   @Override
   public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
      ItemStack map = findFilledMap(input).copyWithCount(1);
      map.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
      return map;
   }

   private static ItemStack findFilledMap(final CraftingInput input) {
      for (int i = 0; i < input.size(); i++) {
         ItemStack itemStack = input.getItem(i);
         if (itemStack.has(DataComponents.MAP_ID)) {
            return itemStack;
         }
      }

      return ItemStack.EMPTY;
   }

   @Override
   public boolean isSpecial() {
      return true;
   }

   @Override
   public RecipeSerializer<MapExtendingRecipe> getSerializer() {
      return RecipeSerializer.MAP_EXTENDING;
   }
}
