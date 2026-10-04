package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.feline.CatModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

public class CatCollarLayer extends RenderLayer<CatRenderState, CatModel> {
   private static final Identifier CAT_COLLAR_LOCATION = Identifier.withDefaultNamespace("textures/entity/cat/cat_collar.png");
   private final CatModel adultModel;
   private final CatModel babyModel;

   public CatCollarLayer(final RenderLayerParent<CatRenderState, CatModel> renderer, final EntityModelSet modelSet) {
      super(renderer);
      this.adultModel = new CatModel(modelSet.bakeLayer(ModelLayers.CAT_COLLAR));
      this.babyModel = new CatModel(modelSet.bakeLayer(ModelLayers.CAT_BABY_COLLAR));
   }

   public void submit(
      final PoseStack poseStack,
      final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      final CatRenderState state,
      final float yRot,
      final float xRot
   ) {
      DyeColor collarColor = state.collarColor;
      if (collarColor != null) {
         int color = collarColor.getTextureDiffuseColor();
         CatModel model = state.isBaby ? this.babyModel : this.adultModel;
         coloredCutoutModelCopyLayerRender(model, CAT_COLLAR_LOCATION, poseStack, submitNodeCollector, lightCoords, state, color, 1);
      }
   }
}
