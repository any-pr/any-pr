package net.minecraft.client.renderer.block.model;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.util.RandomSource;

public class SingleVariant implements BlockStateModel {
   private final BlockModelPart model;

   public SingleVariant(final BlockModelPart model) {
      this.model = model;
   }

   @Override
   public void collectParts(final RandomSource random, final List<BlockModelPart> output) {
      output.add(this.model);
   }

   @Override
   public TextureAtlasSprite particleIcon() {
      return this.model.particleIcon();
   }

   public record Unbaked(Variant variant) implements BlockStateModel.Unbaked {
      public static final Codec<SingleVariant.Unbaked> CODEC = Variant.CODEC.xmap(SingleVariant.Unbaked::new, SingleVariant.Unbaked::variant);

      @Override
      public BlockStateModel bake(final ModelBaker modelBakery) {
         return new SingleVariant(this.variant.bake(modelBakery));
      }

      @Override
      public void resolveDependencies(final ResolvableModel.Resolver resolver) {
         this.variant.resolveDependencies(resolver);
      }
   }
}
