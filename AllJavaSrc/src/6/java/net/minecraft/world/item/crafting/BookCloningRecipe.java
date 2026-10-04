package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

public class BookCloningRecipe extends CustomRecipe {
   public BookCloningRecipe(final CraftingBookCategory category) {
      super(category);
   }

   public boolean matches(final CraftingInput input, final Level level) {
      if (input.ingredientCount() < 2) {
         return false;
      }

      boolean hasEmptyBooks = false;
      boolean hasSourceBook = false;

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (!itemStack.isEmpty()) {
            if (itemStack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
               if (hasSourceBook) {
                  return false;
               }

               hasSourceBook = true;
            } else {
               if (!itemStack.is(ItemTags.BOOK_CLONING_TARGET)) {
                  return false;
               }

               hasEmptyBooks = true;
            }
         }
      }

      return hasSourceBook && hasEmptyBooks;
   }

   public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
      int count = 0;
      ItemStack source = ItemStack.EMPTY;

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (!itemStack.isEmpty()) {
            if (itemStack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
               if (!source.isEmpty()) {
                  return ItemStack.EMPTY;
               }

               source = itemStack;
            } else {
               if (!itemStack.is(ItemTags.BOOK_CLONING_TARGET)) {
                  return ItemStack.EMPTY;
               }

               count++;
            }
         }
      }

      WrittenBookContent sourceContent = source.get(DataComponents.WRITTEN_BOOK_CONTENT);
      if (!source.isEmpty() && count >= 1 && sourceContent != null) {
         WrittenBookContent copiedContent = sourceContent.tryCraftCopy();
         if (copiedContent == null) {
            return ItemStack.EMPTY;
         }

         ItemStack result = source.copyWithCount(count);
         result.set(DataComponents.WRITTEN_BOOK_CONTENT, copiedContent);
         return result;
      } else {
         return ItemStack.EMPTY;
      }
   }

   @Override
   public NonNullList<ItemStack> getRemainingItems(final CraftingInput input) {
      NonNullList<ItemStack> result = NonNullList.withSize(input.size(), ItemStack.EMPTY);

      for (int slot = 0; slot < result.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         ItemStack remainder = itemStack.getItem().getCraftingRemainder();
         if (!remainder.isEmpty()) {
            result.set(slot, remainder);
         } else if (itemStack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            result.set(slot, itemStack.copyWithCount(1));
            break;
         }
      }

      return result;
   }

   @Override
   public RecipeSerializer<BookCloningRecipe> getSerializer() {
      return RecipeSerializer.BOOK_CLONING;
   }
}
