package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.animal.turtle.TurtleModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.TurtleRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.turtle.Turtle;

public class TurtleRenderer extends AgeableMobRenderer<Turtle, TurtleRenderState, TurtleModel> {
   private static final Identifier TURTLE_LOCATION = Identifier.withDefaultNamespace("textures/entity/turtle/big_sea_turtle.png");

   public TurtleRenderer(final EntityRendererProvider.Context context) {
      super(context, new TurtleModel(context.bakeLayer(ModelLayers.TURTLE)), new TurtleModel(context.bakeLayer(ModelLayers.TURTLE_BABY)), 0.7F);
   }

   protected float getShadowRadius(final TurtleRenderState state) {
      float radius = super.getShadowRadius(state);
      return state.isBaby ? radius * 0.83F : radius;
   }

   public TurtleRenderState createRenderState() {
      return new TurtleRenderState();
   }

   public void extractRenderState(final Turtle entity, final TurtleRenderState state, final float partialTicks) {
      super.extractRenderState(entity, state, partialTicks);
      state.isOnLand = !entity.isInWater() && entity.onGround();
      state.isLayingEgg = entity.isLayingEgg();
      state.hasEgg = !entity.isBaby() && entity.hasEgg();
   }

   public Identifier getTextureLocation(final TurtleRenderState state) {
      return TURTLE_LOCATION;
   }
}
