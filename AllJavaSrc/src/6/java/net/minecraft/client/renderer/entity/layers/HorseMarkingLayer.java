package net.minecraft.client.renderer.entity.layers;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.model.animal.equine.HorseModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.equine.Markings;

public class HorseMarkingLayer extends RenderLayer<HorseRenderState, HorseModel> {
   private static final Identifier INVISIBLE_TEXTURE = Identifier.withDefaultNamespace("invisible");
   private static final Map<Markings, Identifier> TEXTURE_BY_MARKINGS = Maps.newEnumMap(
      Map.of(
         Markings.NONE,
         INVISIBLE_TEXTURE,
         Markings.WHITE,
         Identifier.withDefaultNamespace("textures/entity/horse/horse_markings_white.png"),
         Markings.WHITE_FIELD,
         Identifier.withDefaultNamespace("textures/entity/horse/horse_markings_whitefield.png"),
         Markings.WHITE_DOTS,
         Identifier.withDefaultNamespace("textures/entity/horse/horse_markings_whitedots.png"),
         Markings.BLACK_DOTS,
         Identifier.withDefaultNamespace("textures/entity/horse/horse_markings_blackdots.png")
      )
   );

   public HorseMarkingLayer(final RenderLayerParent<HorseRenderState, HorseModel> renderer) {
      super(renderer);
   }

   public void submit(
      final PoseStack poseStack,
      final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      final HorseRenderState state,
      final float yRot,
      final float xRot
   ) {
      Identifier texture = TEXTURE_BY_MARKINGS.get(state.markings);
      if (texture != INVISIBLE_TEXTURE && !state.isInvisible) {
         submitNodeCollector.order(1)
            .submitModel(
               this.getParentModel(),
               state,
               poseStack,
               RenderTypes.entityTranslucent(texture),
               lightCoords,
               LivingEntityRenderer.getOverlayCoords(state, 0.0F),
               -1,
               null,
               state.outlineColor,
               null
            );
      }
   }
}
