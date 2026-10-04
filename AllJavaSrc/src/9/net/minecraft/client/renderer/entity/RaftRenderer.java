package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.object.boat.RaftModel;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public class RaftRenderer extends AbstractBoatRenderer {
   private final EntityModel<BoatRenderState> model;
   private final Identifier texture;

   public RaftRenderer(final EntityRendererProvider.Context context, final ModelLayerLocation modelId) {
      super(context);
      this.texture = modelId.model().withPath(p -> "textures/entity/" + p + ".png");
      this.model = new RaftModel(context.bakeLayer(modelId));
   }

   @Override
   protected EntityModel<BoatRenderState> model() {
      return this.model;
   }

   @Override
   protected RenderType renderType() {
      return this.model.renderType(this.texture);
   }
}
