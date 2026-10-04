package net.minecraft.world.item.crafting;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class RepairItemRecipe extends CustomRecipe {
   public RepairItemRecipe(final CraftingBookCategory category) {
      super(category);
   }

   private static @Nullable Pair<ItemStack, ItemStack> getItemsToCombine(final CraftingInput input) {
      if (input.ingredientCount() != 2) {
         return null;
      }

      ItemStack first = null;

      for (int i = 0; i < input.size(); i++) {
         ItemStack itemStack = input.getItem(i);
         if (!itemStack.isEmpty()) {
            if (first != null) {
               return canCombine(first, itemStack) ? Pair.of(first, itemStack) : null;
            }

            first = itemStack;
         }
      }

      return null;
   }

   private static boolean canCombine(final ItemStack first, final ItemStack second) {
      return second.is(first.getItem())
         && first.getCount() == 1
         && second.getCount() == 1
         && first.has(DataComponents.MAX_DAMAGE)
         && second.has(DataComponents.MAX_DAMAGE)
         && first.has(DataComponents.DAMAGE)
         && second.has(DataComponents.DAMAGE);
   }

   public boolean matches(final CraftingInput input, final Level level) {
      return getItemsToCombine(input) != null;
   }

   public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
      Pair<ItemStack, ItemStack> itemsToCombine = getItemsToCombine(input);
      if (itemsToCombine == null) {
         return ItemStack.EMPTY;
      }

      ItemStack first = (ItemStack)itemsToCombine.getFirst();
      ItemStack second = (ItemStack)itemsToCombine.getSecond();
      int durability = Math.max(first.getMaxDamage(), second.getMaxDamage());
      int remaining1 = first.getMaxDamage() - first.getDamageValue();
      int remaining2 = second.getMaxDamage() - second.getDamageValue();
      int remaining = remaining1 + remaining2 + durability * 5 / 100;
      ItemStack itemStack = new ItemStack(first.getItem());
      itemStack.set(DataComponents.MAX_DAMAGE, durability);
      itemStack.setDamageValue(Math.max(durability - remaining, 0));
      ItemEnchantments firstEnchants = EnchantmentHelper.getEnchantmentsForCrafting(first);
      ItemEnchantments secondEnchants = EnchantmentHelper.getEnchantmentsForCrafting(second);
      EnchantmentHelper.updateEnchantments(
         itemStack,
         enchantments -> registries.lookupOrThrow(Registries.ENCHANTMENT).listElements().filter(h -> h.is(EnchantmentTags.CURSE)).forEach(enchant -> {
            int enchantLevel = Math.max(firstEnchants.getLevel(enchant), secondEnchants.getLevel(enchant));
            if (enchantLevel > 0) {
               enchantments.upgrade(enchant, enchantLevel);
            }
         })
      );
      return itemStack;
   }

   @Override
   public RecipeSerializer<RepairItemRecipe> getSerializer() {
      return RecipeSerializer.REPAIR_ITEM;
   }
}
