package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.animal.sheep.SheepFurModel;
import net.minecraft.client.model.animal.sheep.SheepModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public class SheepWoolLayer extends RenderLayer<SheepRenderState, SheepModel> {
   private static final Identifier SHEEP_WOOL_LOCATION = Identifier.withDefaultNamespace("textures/entity/sheep/sheep_wool.png");
   private final EntityModel<SheepRenderState> adultModel;
   private final EntityModel<SheepRenderState> babyModel;

   public SheepWoolLayer(final RenderLayerParent<SheepRenderState, SheepModel> renderer, final EntityModelSet modelSet) {
      super(renderer);
      this.adultModel = new SheepFurModel(modelSet.bakeLayer(ModelLayers.SHEEP_WOOL));
      this.babyModel = new SheepFurModel(modelSet.bakeLayer(ModelLayers.SHEEP_BABY_WOOL));
   }

   public void submit(
      final PoseStack poseStack,
      final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      final SheepRenderState state,
      final float yRot,
      final float xRot
   ) {
      if (!state.isSheared) {
         EntityModel<SheepRenderState> model = state.isBaby ? this.babyModel : this.adultModel;
         if (state.isInvisible) {
            if (state.appearsGlowing()) {
               submitNodeCollector.submitModel(
                  model,
                  state,
                  poseStack,
                  RenderTypes.outline(SHEEP_WOOL_LOCATION),
                  lightCoords,
                  LivingEntityRenderer.getOverlayCoords(state, 0.0F),
                  -16777216,
                  null,
                  state.outlineColor,
                  null
               );
            }
         } else {
            coloredCutoutModelCopyLayerRender(model, SHEEP_WOOL_LOCATION, poseStack, submitNodeCollector, lightCoords, state, state.getWoolColor(), 0);
         }
      }
   }
}
