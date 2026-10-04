package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;

public class DecoratedPotRecipe extends CustomRecipe {
   public DecoratedPotRecipe(final CraftingBookCategory category) {
      super(category);
   }

   private static ItemStack back(final CraftingInput input) {
      return input.getItem(1, 0);
   }

   private static ItemStack left(final CraftingInput input) {
      return input.getItem(0, 1);
   }

   private static ItemStack right(final CraftingInput input) {
      return input.getItem(2, 1);
   }

   private static ItemStack front(final CraftingInput input) {
      return input.getItem(1, 2);
   }

   public boolean matches(final CraftingInput input, final Level level) {
      return input.width() == 3 && input.height() == 3 && input.ingredientCount() == 4
         ? back(input).is(ItemTags.DECORATED_POT_INGREDIENTS)
            && left(input).is(ItemTags.DECORATED_POT_INGREDIENTS)
            && right(input).is(ItemTags.DECORATED_POT_INGREDIENTS)
            && front(input).is(ItemTags.DECORATED_POT_INGREDIENTS)
         : false;
   }

   public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
      PotDecorations decorations = new PotDecorations(back(input).getItem(), left(input).getItem(), right(input).getItem(), front(input).getItem());
      return DecoratedPotBlockEntity.createDecoratedPotItem(decorations);
   }

   @Override
   public RecipeSerializer<DecoratedPotRecipe> getSerializer() {
      return RecipeSerializer.DECORATED_POT_RECIPE;
   }
}
