package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class TransmuteRecipe implements CraftingRecipe {
   private final String group;
   private final CraftingBookCategory category;
   private final Ingredient input;
   private final Ingredient material;
   private final TransmuteResult result;
   private @Nullable PlacementInfo placementInfo;

   public TransmuteRecipe(
      final String group, final CraftingBookCategory category, final Ingredient input, final Ingredient material, final TransmuteResult result
   ) {
      this.group = group;
      this.category = category;
      this.input = input;
      this.material = material;
      this.result = result;
   }

   public boolean matches(final CraftingInput input, final Level level) {
      if (input.ingredientCount() != 2) {
         return false;
      }

      boolean foundInput = false;
      boolean foundMaterial = false;

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack stack = input.getItem(slot);
         if (!stack.isEmpty()) {
            if (!foundInput && this.input.test(stack)) {
               if (this.result.isResultUnchanged(stack)) {
                  return false;
               }

               foundInput = true;
            } else {
               if (foundMaterial || !this.material.test(stack)) {
                  return false;
               }

               foundMaterial = true;
            }
         }
      }

      return foundInput && foundMaterial;
   }

   public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack itemStack = input.getItem(slot);
         if (!itemStack.isEmpty() && this.input.test(itemStack)) {
            return this.result.apply(itemStack);
         }
      }

      return ItemStack.EMPTY;
   }

   @Override
   public List<RecipeDisplay> display() {
      return List.of(
         new ShapelessCraftingRecipeDisplay(
            List.of(this.input.display(), this.material.display()), this.result.display(), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
         )
      );
   }

   @Override
   public RecipeSerializer<TransmuteRecipe> getSerializer() {
      return RecipeSerializer.TRANSMUTE;
   }

   @Override
   public String group() {
      return this.group;
   }

   @Override
   public PlacementInfo placementInfo() {
      if (this.placementInfo == null) {
         this.placementInfo = PlacementInfo.create(List.of(this.input, this.material));
      }

      return this.placementInfo;
   }

   @Override
   public CraftingBookCategory category() {
      return this.category;
   }

   public static class Serializer implements RecipeSerializer<TransmuteRecipe> {
      private static final MapCodec<TransmuteRecipe> CODEC = RecordCodecBuilder.mapCodec(
         r -> r.group(
               Codec.STRING.optionalFieldOf("group", "").forGetter(o -> o.group),
               CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(o -> o.category),
               Ingredient.CODEC.fieldOf("input").forGetter(o -> o.input),
               Ingredient.CODEC.fieldOf("material").forGetter(o -> o.material),
               TransmuteResult.CODEC.fieldOf("result").forGetter(o -> o.result)
            )
            .apply(r, TransmuteRecipe::new)
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, TransmuteRecipe> STREAM_CODEC = StreamCodec.composite(
         ByteBufCodecs.STRING_UTF8,
         r -> r.group,
         CraftingBookCategory.STREAM_CODEC,
         r -> r.category,
         Ingredient.CONTENTS_STREAM_CODEC,
         r -> r.input,
         Ingredient.CONTENTS_STREAM_CODEC,
         r -> r.material,
         TransmuteResult.STREAM_CODEC,
         r -> r.result,
         TransmuteRecipe::new
      );

      @Override
      public MapCodec<TransmuteRecipe> codec() {
         return CODEC;
      }

      @Override
      public StreamCodec<RegistryFriendlyByteBuf, TransmuteRecipe> streamCodec() {
         return STREAM_CODEC;
      }
   }
}
