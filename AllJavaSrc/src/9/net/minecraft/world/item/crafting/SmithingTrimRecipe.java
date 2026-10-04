package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.jspecify.annotations.Nullable;

public class SmithingTrimRecipe implements SmithingRecipe {
   private final Ingredient template;
   private final Ingredient base;
   private final Ingredient addition;
   private final Holder<TrimPattern> pattern;
   private @Nullable PlacementInfo placementInfo;

   public SmithingTrimRecipe(final Ingredient template, final Ingredient base, final Ingredient addition, final Holder<TrimPattern> pattern) {
      this.template = template;
      this.base = base;
      this.addition = addition;
      this.pattern = pattern;
   }

   public ItemStack assemble(final SmithingRecipeInput input, final HolderLookup.Provider registries) {
      return applyTrim(registries, input.base(), input.addition(), this.pattern);
   }

   public static ItemStack applyTrim(
      final HolderLookup.Provider registries, final ItemStack baseItem, final ItemStack materialItem, final Holder<TrimPattern> pattern
   ) {
      Optional<Holder<TrimMaterial>> material = TrimMaterials.getFromIngredient(registries, materialItem);
      if (material.isPresent()) {
         ArmorTrim existingTrim = baseItem.get(DataComponents.TRIM);
         ArmorTrim newTrim = new ArmorTrim(material.get(), pattern);
         if (Objects.equals(existingTrim, newTrim)) {
            return ItemStack.EMPTY;
         }

         ItemStack trimmedItem = baseItem.copyWithCount(1);
         trimmedItem.set(DataComponents.TRIM, newTrim);
         return trimmedItem;
      } else {
         return ItemStack.EMPTY;
      }
   }

   @Override
   public Optional<Ingredient> templateIngredient() {
      return Optional.of(this.template);
   }

   @Override
   public Ingredient baseIngredient() {
      return this.base;
   }

   @Override
   public Optional<Ingredient> additionIngredient() {
      return Optional.of(this.addition);
   }

   @Override
   public RecipeSerializer<SmithingTrimRecipe> getSerializer() {
      return RecipeSerializer.SMITHING_TRIM;
   }

   @Override
   public PlacementInfo placementInfo() {
      if (this.placementInfo == null) {
         this.placementInfo = PlacementInfo.create(List.of(this.template, this.base, this.addition));
      }

      return this.placementInfo;
   }

   @Override
   public List<RecipeDisplay> display() {
      SlotDisplay base = this.base.display();
      SlotDisplay material = this.addition.display();
      SlotDisplay template = this.template.display();
      return List.of(
         new SmithingRecipeDisplay(
            template,
            base,
            material,
            new SlotDisplay.SmithingTrimDemoSlotDisplay(base, material, this.pattern),
            new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)
         )
      );
   }

   public static class Serializer implements RecipeSerializer<SmithingTrimRecipe> {
      private static final MapCodec<SmithingTrimRecipe> CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(
               Ingredient.CODEC.fieldOf("template").forGetter(r -> r.template),
               Ingredient.CODEC.fieldOf("base").forGetter(r -> r.base),
               Ingredient.CODEC.fieldOf("addition").forGetter(r -> r.addition),
               TrimPattern.CODEC.fieldOf("pattern").forGetter(r -> r.pattern)
            )
            .apply(i, SmithingTrimRecipe::new)
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> STREAM_CODEC = StreamCodec.composite(
         Ingredient.CONTENTS_STREAM_CODEC,
         r -> r.template,
         Ingredient.CONTENTS_STREAM_CODEC,
         r -> r.base,
         Ingredient.CONTENTS_STREAM_CODEC,
         r -> r.addition,
         TrimPattern.STREAM_CODEC,
         r -> r.pattern,
         SmithingTrimRecipe::new
      );

      @Override
      public MapCodec<SmithingTrimRecipe> codec() {
         return CODEC;
      }

      @Override
      public StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> streamCodec() {
         return STREAM_CODEC;
      }
   }
}
