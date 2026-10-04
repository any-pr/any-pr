package net.minecraft.client.resources.model;

import java.util.List;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;

public class WeightedVariants implements BlockStateModel {
   private final WeightedList<BlockStateModel> list;
   private final TextureAtlasSprite particleIcon;

   public WeightedVariants(final WeightedList<BlockStateModel> list) {
      this.list = list;
      BlockStateModel firstModel = list.unwrap().getFirst().value();
      this.particleIcon = firstModel.particleIcon();
   }

   @Override
   public TextureAtlasSprite particleIcon() {
      return this.particleIcon;
   }

   @Override
   public void collectParts(final RandomSource random, final List<BlockModelPart> output) {
      this.list.getRandomOrThrow(random).collectParts(random, output);
   }

   public record Unbaked(WeightedList<BlockStateModel.Unbaked> entries) implements BlockStateModel.Unbaked {
      @Override
      public BlockStateModel bake(final ModelBaker modelBakery) {
         return new WeightedVariants(this.entries.map(m -> m.bake(modelBakery)));
      }

      @Override
      public void resolveDependencies(final ResolvableModel.Resolver resolver) {
         this.entries.unwrap().forEach(v -> v.value().resolveDependencies(resolver));
      }
   }
}
