package net.minecraft.data;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

public class BlockFamily {
   private final Block baseBlock;
   private final Map<BlockFamily.Variant, Block> variants = Maps.newHashMap();
   private boolean generateModel = true;
   private boolean generateRecipe = true;
   private @Nullable String recipeGroupPrefix;
   private @Nullable String recipeUnlockedBy;

   private BlockFamily(final Block baseBlock) {
      this.baseBlock = baseBlock;
   }

   public Block getBaseBlock() {
      return this.baseBlock;
   }

   public Map<BlockFamily.Variant, Block> getVariants() {
      return this.variants;
   }

   public Block get(final BlockFamily.Variant variant) {
      return this.variants.get(variant);
   }

   public boolean shouldGenerateModel() {
      return this.generateModel;
   }

   public boolean shouldGenerateRecipe() {
      return this.generateRecipe;
   }

   public Optional<String> getRecipeGroupPrefix() {
      return StringUtil.isBlank(this.recipeGroupPrefix) ? Optional.empty() : Optional.of(this.recipeGroupPrefix);
   }

   public Optional<String> getRecipeUnlockedBy() {
      return StringUtil.isBlank(this.recipeUnlockedBy) ? Optional.empty() : Optional.of(this.recipeUnlockedBy);
   }

   public static class Builder {
      private final BlockFamily family;

      public Builder(final Block baseBlock) {
         this.family = new BlockFamily(baseBlock);
      }

      public BlockFamily getFamily() {
         return this.family;
      }

      public BlockFamily.Builder button(final Block button) {
         this.family.variants.put(BlockFamily.Variant.BUTTON, button);
         return this;
      }

      public BlockFamily.Builder chiseled(final Block chiseled) {
         this.family.variants.put(BlockFamily.Variant.CHISELED, chiseled);
         return this;
      }

      public BlockFamily.Builder mosaic(final Block mosaic) {
         this.family.variants.put(BlockFamily.Variant.MOSAIC, mosaic);
         return this;
      }

      public BlockFamily.Builder cracked(final Block cracked) {
         this.family.variants.put(BlockFamily.Variant.CRACKED, cracked);
         return this;
      }

      public BlockFamily.Builder cut(final Block cut) {
         this.family.variants.put(BlockFamily.Variant.CUT, cut);
         return this;
      }

      public BlockFamily.Builder door(final Block door) {
         this.family.variants.put(BlockFamily.Variant.DOOR, door);
         return this;
      }

      public BlockFamily.Builder customFence(final Block fence) {
         this.family.variants.put(BlockFamily.Variant.CUSTOM_FENCE, fence);
         return this;
      }

      public BlockFamily.Builder fence(final Block fence) {
         this.family.variants.put(BlockFamily.Variant.FENCE, fence);
         return this;
      }

      public BlockFamily.Builder customFenceGate(final Block fenceGate) {
         this.family.variants.put(BlockFamily.Variant.CUSTOM_FENCE_GATE, fenceGate);
         return this;
      }

      public BlockFamily.Builder fenceGate(final Block fenceGate) {
         this.family.variants.put(BlockFamily.Variant.FENCE_GATE, fenceGate);
         return this;
      }

      public BlockFamily.Builder sign(final Block sign, final Block wallSign) {
         this.family.variants.put(BlockFamily.Variant.SIGN, sign);
         this.family.variants.put(BlockFamily.Variant.WALL_SIGN, wallSign);
         return this;
      }

      public BlockFamily.Builder slab(final Block slab) {
         this.family.variants.put(BlockFamily.Variant.SLAB, slab);
         return this;
      }

      public BlockFamily.Builder stairs(final Block stairs) {
         this.family.variants.put(BlockFamily.Variant.STAIRS, stairs);
         return this;
      }

      public BlockFamily.Builder pressurePlate(final Block pressurePlate) {
         this.family.variants.put(BlockFamily.Variant.PRESSURE_PLATE, pressurePlate);
         return this;
      }

      public BlockFamily.Builder polished(final Block polished) {
         this.family.variants.put(BlockFamily.Variant.POLISHED, polished);
         return this;
      }

      public BlockFamily.Builder trapdoor(final Block trapdoor) {
         this.family.variants.put(BlockFamily.Variant.TRAPDOOR, trapdoor);
         return this;
      }

      public BlockFamily.Builder wall(final Block wall) {
         this.family.variants.put(BlockFamily.Variant.WALL, wall);
         return this;
      }

      public BlockFamily.Builder dontGenerateModel() {
         this.family.generateModel = false;
         return this;
      }

      public BlockFamily.Builder dontGenerateRecipe() {
         this.family.generateRecipe = false;
         return this;
      }

      public BlockFamily.Builder recipeGroupPrefix(final String recipeGroupPrefix) {
         this.family.recipeGroupPrefix = recipeGroupPrefix;
         return this;
      }

      public BlockFamily.Builder recipeUnlockedBy(final String recipeUnlockedBy) {
         this.family.recipeUnlockedBy = recipeUnlockedBy;
         return this;
      }
   }

   public enum Variant {
      BUTTON("button"),
      CHISELED("chiseled"),
      CRACKED("cracked"),
      CUT("cut"),
      DOOR("door"),
      CUSTOM_FENCE("fence"),
      FENCE("fence"),
      CUSTOM_FENCE_GATE("fence_gate"),
      FENCE_GATE("fence_gate"),
      MOSAIC("mosaic"),
      SIGN("sign"),
      SLAB("slab"),
      STAIRS("stairs"),
      PRESSURE_PLATE("pressure_plate"),
      POLISHED("polished"),
      TRAPDOOR("trapdoor"),
      WALL("wall"),
      WALL_SIGN("wall_sign");

      private final String recipeGroup;

      Variant(final String recipeGroup) {
         this.recipeGroup = recipeGroup;
      }

      public String getRecipeGroup() {
         return this.recipeGroup;
      }
   }
}
