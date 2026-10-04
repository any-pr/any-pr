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

public class ShapelessRecipe implements CraftingRecipe {
   private final String group;
   private final CraftingBookCategory category;
   private final ItemStack result;
   private final List<Ingredient> ingredients;
   private @Nullable PlacementInfo placementInfo;

   public ShapelessRecipe(final String group, final CraftingBookCategory category, final ItemStack result, final List<Ingredient> ingredients) {
      this.group = group;
      this.category = category;
      this.result = result;
      this.ingredients = ingredients;
   }

   @Override
   public RecipeSerializer<ShapelessRecipe> getSerializer() {
      return RecipeSerializer.SHAPELESS_RECIPE;
   }

   @Override
   public String group() {
      return this.group;
   }

   @Override
   public CraftingBookCategory category() {
      return this.category;
   }

   @Override
   public PlacementInfo placementInfo() {
      if (this.placementInfo == null) {
         this.placementInfo = PlacementInfo.create(this.ingredients);
      }

      return this.placementInfo;
   }

   public boolean matches(final CraftingInput input, final Level level) {
      if (input.ingredientCount() != this.ingredients.size()) {
         return false;
      } else {
         return input.size() == 1 && this.ingredients.size() == 1
            ? this.ingredients.getFirst().test(input.getItem(0))
            : input.stackedContents().canCraft(this, null);
      }
   }

   public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
      return this.result.copy();
   }

   @Override
   public List<RecipeDisplay> display() {
      return List.of(
         new ShapelessCraftingRecipeDisplay(
            this.ingredients.stream().map(Ingredient::display).toList(),
            new SlotDisplay.ItemStackSlotDisplay(this.result),
            new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
         )
      );
   }

   public static class Serializer implements RecipeSerializer<ShapelessRecipe> {
      private static final MapCodec<ShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(
         r -> r.group(
               Codec.STRING.optionalFieldOf("group", "").forGetter(o -> o.group),
               CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(o -> o.category),
               ItemStack.STRICT_CODEC.fieldOf("result").forGetter(o -> o.result),
               Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(o -> o.ingredients)
            )
            .apply(r, ShapelessRecipe::new)
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, ShapelessRecipe> STREAM_CODEC = StreamCodec.composite(
         ByteBufCodecs.STRING_UTF8,
         r -> r.group,
         CraftingBookCategory.STREAM_CODEC,
         r -> r.category,
         ItemStack.STREAM_CODEC,
         r -> r.result,
         Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
         r -> r.ingredients,
         ShapelessRecipe::new
      );

      @Override
      public MapCodec<ShapelessRecipe> codec() {
         return CODEC;
      }

      @Override
      public StreamCodec<RegistryFriendlyByteBuf, ShapelessRecipe> streamCodec() {
         return STREAM_CODEC;
      }
   }
}
