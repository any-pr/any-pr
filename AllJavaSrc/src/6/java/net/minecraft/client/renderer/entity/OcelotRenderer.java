package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.animal.feline.OcelotModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.feline.Ocelot;

public class OcelotRenderer extends AgeableMobRenderer<Ocelot, FelineRenderState, OcelotModel> {
   private static final Identifier CAT_OCELOT_LOCATION = Identifier.withDefaultNamespace("textures/entity/cat/ocelot.png");

   public OcelotRenderer(final EntityRendererProvider.Context context) {
      super(context, new OcelotModel(context.bakeLayer(ModelLayers.OCELOT)), new OcelotModel(context.bakeLayer(ModelLayers.OCELOT_BABY)), 0.4F);
   }

   public Identifier getTextureLocation(final FelineRenderState state) {
      return CAT_OCELOT_LOCATION;
   }

   public FelineRenderState createRenderState() {
      return new FelineRenderState();
   }

   public void extractRenderState(final Ocelot entity, final FelineRenderState state, final float partialTicks) {
      super.extractRenderState(entity, state, partialTicks);
      state.isCrouching = entity.isCrouching();
      state.isSprinting = entity.isSprinting();
   }
}
