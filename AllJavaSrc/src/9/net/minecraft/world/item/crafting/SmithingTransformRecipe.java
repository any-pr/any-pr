package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import org.jspecify.annotations.Nullable;

public class SmithingTransformRecipe implements SmithingRecipe {
   private final Optional<Ingredient> template;
   private final Ingredient base;
   private final Optional<Ingredient> addition;
   private final TransmuteResult result;
   private @Nullable PlacementInfo placementInfo;

   public SmithingTransformRecipe(final Optional<Ingredient> template, final Ingredient base, final Optional<Ingredient> addition, final TransmuteResult result) {
      this.template = template;
      this.base = base;
      this.addition = addition;
      this.result = result;
   }

   public ItemStack assemble(final SmithingRecipeInput input, final HolderLookup.Provider registries) {
      return this.result.apply(input.base());
   }

   @Override
   public Optional<Ingredient> templateIngredient() {
      return this.template;
   }

   @Override
   public Ingredient baseIngredient() {
      return this.base;
   }

   @Override
   public Optional<Ingredient> additionIngredient() {
      return this.addition;
   }

   @Override
   public RecipeSerializer<SmithingTransformRecipe> getSerializer() {
      return RecipeSerializer.SMITHING_TRANSFORM;
   }

   @Override
   public PlacementInfo placementInfo() {
      if (this.placementInfo == null) {
         this.placementInfo = PlacementInfo.createFromOptionals(List.of(this.template, Optional.of(this.base), this.addition));
      }

      return this.placementInfo;
   }

   @Override
   public List<RecipeDisplay> display() {
      return List.of(
         new SmithingRecipeDisplay(
            Ingredient.optionalIngredientToDisplay(this.template),
            this.base.display(),
            Ingredient.optionalIngredientToDisplay(this.addition),
            this.result.display(),
            new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)
         )
      );
   }

   public static class Serializer implements RecipeSerializer<SmithingTransformRecipe> {
      private static final MapCodec<SmithingTransformRecipe> CODEC = RecordCodecBuilder.mapCodec(
         r -> r.group(
               Ingredient.CODEC.optionalFieldOf("template").forGetter(o -> o.template),
               Ingredient.CODEC.fieldOf("base").forGetter(o -> o.base),
               Ingredient.CODEC.optionalFieldOf("addition").forGetter(o -> o.addition),
               TransmuteResult.CODEC.fieldOf("result").forGetter(o -> o.result)
            )
            .apply(r, SmithingTransformRecipe::new)
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, SmithingTransformRecipe> STREAM_CODEC = StreamCodec.composite(
         Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,
         r -> r.template,
         Ingredient.CONTENTS_STREAM_CODEC,
         r -> r.base,
         Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC,
         r -> r.addition,
         TransmuteResult.STREAM_CODEC,
         r -> r.result,
         SmithingTransformRecipe::new
      );

      @Override
      public MapCodec<SmithingTransformRecipe> codec() {
         return CODEC;
      }

      @Override
      public StreamCodec<RegistryFriendlyByteBuf, SmithingTransformRecipe> streamCodec() {
         return STREAM_CODEC;
      }
   }
}
