package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public abstract class SingleItemRecipe implements Recipe<SingleRecipeInput> {
   private final Ingredient input;
   private final ItemStack result;
   private final String group;
   private @Nullable PlacementInfo placementInfo;

   public SingleItemRecipe(final String group, final Ingredient input, final ItemStack result) {
      this.group = group;
      this.input = input;
      this.result = result;
   }

   @Override
   public abstract RecipeSerializer<? extends SingleItemRecipe> getSerializer();

   @Override
   public abstract RecipeType<? extends SingleItemRecipe> getType();

   public boolean matches(final SingleRecipeInput input, final Level level) {
      return this.input.test(input.item());
   }

   @Override
   public String group() {
      return this.group;
   }

   public Ingredient input() {
      return this.input;
   }

   protected ItemStack result() {
      return this.result;
   }

   @Override
   public PlacementInfo placementInfo() {
      if (this.placementInfo == null) {
         this.placementInfo = PlacementInfo.create(this.input);
      }

      return this.placementInfo;
   }

   public ItemStack assemble(final SingleRecipeInput input, final HolderLookup.Provider registries) {
      return this.result.copy();
   }

   @FunctionalInterface
   public interface Factory<T extends SingleItemRecipe> {
      T create(String group, Ingredient ingredient, ItemStack result);
   }

   public static class Serializer<T extends SingleItemRecipe> implements RecipeSerializer<T> {
      private final MapCodec<T> codec;
      private final StreamCodec<RegistryFriendlyByteBuf, T> streamCodec;

      protected Serializer(final SingleItemRecipe.Factory<T> factory) {
         this.codec = RecordCodecBuilder.mapCodec(
            r -> r.group(
                  Codec.STRING.optionalFieldOf("group", "").forGetter(SingleItemRecipe::group),
                  Ingredient.CODEC.fieldOf("ingredient").forGetter(SingleItemRecipe::input),
                  ItemStack.STRICT_CODEC.fieldOf("result").forGetter(SingleItemRecipe::result)
               )
               .apply(r, factory::create)
         );
         this.streamCodec = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            SingleItemRecipe::group,
            Ingredient.CONTENTS_STREAM_CODEC,
            SingleItemRecipe::input,
            ItemStack.STREAM_CODEC,
            SingleItemRecipe::result,
            factory::create
         );
      }

      @Override
      public MapCodec<T> codec() {
         return this.codec;
      }

      @Override
      public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
         return this.streamCodec;
      }
   }
}
