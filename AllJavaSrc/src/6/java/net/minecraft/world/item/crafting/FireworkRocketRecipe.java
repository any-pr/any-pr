package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;

public class FireworkRocketRecipe extends CustomRecipe {
   private static final Ingredient PAPER_INGREDIENT = Ingredient.of(Items.PAPER);
   private static final Ingredient GUNPOWDER_INGREDIENT = Ingredient.of(Items.GUNPOWDER);
   private static final Ingredient STAR_INGREDIENT = Ingredient.of(Items.FIREWORK_STAR);

   public FireworkRocketRecipe(final CraftingBookCategory category) {
      super(category);
   }

   public boolean matches(final CraftingInput input, final Level level) {
      if (input.ingredientCount() < 2) {
         return false;
      }

      boolean paper = false;
      int gunPowder = 0;

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (!itemStack.isEmpty()) {
            if (PAPER_INGREDIENT.test(itemStack)) {
               if (paper) {
                  return false;
               }

               paper = true;
            } else if (GUNPOWDER_INGREDIENT.test(itemStack)) {
               if (++gunPowder > 3) {
                  return false;
               }
            } else if (!STAR_INGREDIENT.test(itemStack)) {
               return false;
            }
         }
      }

      return paper && gunPowder >= 1;
   }

   public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
      List<FireworkExplosion> explosions = new ArrayList<>();
      int gunPowder = 0;

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (!itemStack.isEmpty()) {
            if (GUNPOWDER_INGREDIENT.test(itemStack)) {
               gunPowder++;
            } else if (STAR_INGREDIENT.test(itemStack)) {
               FireworkExplosion explosion = itemStack.get(DataComponents.FIREWORK_EXPLOSION);
               if (explosion != null) {
                  explosions.add(explosion);
               }
            }
         }
      }

      ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET, 3);
      rocket.set(DataComponents.FIREWORKS, new Fireworks(gunPowder, explosions));
      return rocket;
   }

   @Override
   public RecipeSerializer<FireworkRocketRecipe> getSerializer() {
      return RecipeSerializer.FIREWORK_ROCKET;
   }
}
