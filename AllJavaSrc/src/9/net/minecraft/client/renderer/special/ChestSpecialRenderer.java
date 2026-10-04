package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3fc;

public class ChestSpecialRenderer implements NoDataSpecialModelRenderer {
   public static final Identifier GIFT_CHEST_TEXTURE = Identifier.withDefaultNamespace("christmas");
   public static final Identifier NORMAL_CHEST_TEXTURE = Identifier.withDefaultNamespace("normal");
   public static final Identifier TRAPPED_CHEST_TEXTURE = Identifier.withDefaultNamespace("trapped");
   public static final Identifier ENDER_CHEST_TEXTURE = Identifier.withDefaultNamespace("ender");
   public static final Identifier COPPER_CHEST_TEXTURE = Identifier.withDefaultNamespace("copper");
   public static final Identifier EXPOSED_COPPER_CHEST_TEXTURE = Identifier.withDefaultNamespace("copper_exposed");
   public static final Identifier WEATHERED_COPPER_CHEST_TEXTURE = Identifier.withDefaultNamespace("copper_weathered");
   public static final Identifier OXIDIZED_COPPER_CHEST_TEXTURE = Identifier.withDefaultNamespace("copper_oxidized");
   private final MaterialSet materials;
   private final ChestModel model;
   private final Material material;
   private final float openness;

   public ChestSpecialRenderer(final MaterialSet materials, final ChestModel model, final Material material, final float openness) {
      this.materials = materials;
      this.model = model;
      this.material = material;
      this.openness = openness;
   }

   @Override
   public void submit(
      final ItemDisplayContext type,
      final PoseStack poseStack,
      final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      final int overlayCoords,
      final boolean hasFoil,
      final int outlineColor
   ) {
      submitNodeCollector.submitModel(
         this.model,
         this.openness,
         poseStack,
         this.material.renderType(RenderTypes::entitySolid),
         lightCoords,
         overlayCoords,
         -1,
         this.materials.get(this.material),
         outlineColor,
         null
      );
   }

   @Override
   public void getExtents(final Consumer<Vector3fc> output) {
      PoseStack poseStack = new PoseStack();
      this.model.setupAnim(this.openness);
      this.model.root().getExtentsForGui(poseStack, output);
   }

   public record Unbaked(Identifier texture, float openness) implements SpecialModelRenderer.Unbaked {
      public static final MapCodec<ChestSpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(
               Identifier.CODEC.fieldOf("texture").forGetter(ChestSpecialRenderer.Unbaked::texture),
               Codec.FLOAT.optionalFieldOf("openness", 0.0F).forGetter(ChestSpecialRenderer.Unbaked::openness)
            )
            .apply(i, ChestSpecialRenderer.Unbaked::new)
      );

      public Unbaked(final Identifier texture) {
         this(texture, 0.0F);
      }

      @Override
      public MapCodec<ChestSpecialRenderer.Unbaked> type() {
         return MAP_CODEC;
      }

      @Override
      public SpecialModelRenderer<?> bake(final SpecialModelRenderer.BakingContext context) {
         ChestModel model = new ChestModel(context.entityModelSet().bakeLayer(ModelLayers.CHEST));
         Material fullTexture = Sheets.CHEST_MAPPER.apply(this.texture);
         return new ChestSpecialRenderer(context.materials(), model, fullTexture, this.openness);
      }
   }
}
