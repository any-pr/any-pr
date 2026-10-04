package net.minecraft.client.renderer.item;

import com.google.common.base.Suppliers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public class SpecialModelWrapper<T> implements ItemModel {
   private final SpecialModelRenderer<T> specialRenderer;
   private final ModelRenderProperties properties;
   private final Supplier<Vector3fc[]> extents;

   public SpecialModelWrapper(final SpecialModelRenderer<T> specialRenderer, final ModelRenderProperties properties) {
      this.specialRenderer = specialRenderer;
      this.properties = properties;
      this.extents = Suppliers.memoize(() -> {
         Set<Vector3fc> results = new HashSet<>();
         specialRenderer.getExtents(results::add);
         return results.toArray(new Vector3fc[0]);
      });
   }

   @Override
   public void update(
      final ItemStackRenderState output,
      final ItemStack item,
      final ItemModelResolver resolver,
      final ItemDisplayContext displayContext,
      final @Nullable ClientLevel level,
      final @Nullable ItemOwner owner,
      final int seed
   ) {
      output.appendModelIdentityElement(this);
      ItemStackRenderState.LayerRenderState layer = output.newLayer();
      if (item.hasFoil()) {
         ItemStackRenderState.FoilType foilType = ItemStackRenderState.FoilType.STANDARD;
         layer.setFoilType(foilType);
         output.setAnimated();
         output.appendModelIdentityElement(foilType);
      }

      T argument = this.specialRenderer.extractArgument(item);
      layer.setExtents(this.extents);
      layer.setupSpecialModel(this.specialRenderer, argument);
      if (argument != null) {
         output.appendModelIdentityElement(argument);
      }

      this.properties.applyToLayer(layer, displayContext);
   }

   public record Unbaked(Identifier base, SpecialModelRenderer.Unbaked specialModel) implements ItemModel.Unbaked {
      public static final MapCodec<SpecialModelWrapper.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(
               Identifier.CODEC.fieldOf("base").forGetter(SpecialModelWrapper.Unbaked::base),
               SpecialModelRenderers.CODEC.fieldOf("model").forGetter(SpecialModelWrapper.Unbaked::specialModel)
            )
            .apply(i, SpecialModelWrapper.Unbaked::new)
      );

      @Override
      public void resolveDependencies(final ResolvableModel.Resolver resolver) {
         resolver.markDependency(this.base);
      }

      @Override
      public ItemModel bake(final ItemModel.BakingContext context) {
         SpecialModelRenderer<?> bakedSpecialModel = this.specialModel.bake(context);
         if (bakedSpecialModel == null) {
            return context.missingItemModel();
         }

         ModelRenderProperties properties = this.getProperties(context);
         return new SpecialModelWrapper<>(bakedSpecialModel, properties);
      }

      private ModelRenderProperties getProperties(final ItemModel.BakingContext context) {
         ModelBaker baker = context.blockModelBaker();
         ResolvedModel model = baker.getModel(this.base);
         TextureSlots textureSlots = model.getTopTextureSlots();
         return ModelRenderProperties.fromResolvedModel(baker, model, textureSlots);
      }

      @Override
      public MapCodec<SpecialModelWrapper.Unbaked> type() {
         return MAP_CODEC;
      }
   }
}
