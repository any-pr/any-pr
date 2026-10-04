package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public abstract class CustomRecipe implements CraftingRecipe {
   private final CraftingBookCategory category;

   public CustomRecipe(final CraftingBookCategory category) {
      this.category = category;
   }

   @Override
   public boolean isSpecial() {
      return true;
   }

   @Override
   public CraftingBookCategory category() {
      return this.category;
   }

   @Override
   public PlacementInfo placementInfo() {
      return PlacementInfo.NOT_PLACEABLE;
   }

   @Override
   public abstract RecipeSerializer<? extends CustomRecipe> getSerializer();

   public static class Serializer<T extends CraftingRecipe> implements RecipeSerializer<T> {
      private final MapCodec<T> codec;
      private final StreamCodec<RegistryFriendlyByteBuf, T> streamCodec;

      public Serializer(final CustomRecipe.Serializer.Factory<T> constructor) {
         this.codec = RecordCodecBuilder.mapCodec(
            r -> r.group(CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(CraftingRecipe::category))
               .apply(r, constructor::create)
         );
         this.streamCodec = StreamCodec.composite(CraftingBookCategory.STREAM_CODEC, CraftingRecipe::category, constructor::create);
      }

      @Override
      public MapCodec<T> codec() {
         return this.codec;
      }

      @Override
      public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
         return this.streamCodec;
      }

      @FunctionalInterface
      public interface Factory<T extends CraftingRecipe> {
         T create(CraftingBookCategory category);
      }
   }
}
