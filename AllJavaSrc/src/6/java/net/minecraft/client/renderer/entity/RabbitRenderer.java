package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.animal.rabbit.RabbitModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.RabbitRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.rabbit.Rabbit;

public class RabbitRenderer extends AgeableMobRenderer<Rabbit, RabbitRenderState, RabbitModel> {
   private static final Identifier RABBIT_BROWN_LOCATION = Identifier.withDefaultNamespace("textures/entity/rabbit/brown.png");
   private static final Identifier RABBIT_WHITE_LOCATION = Identifier.withDefaultNamespace("textures/entity/rabbit/white.png");
   private static final Identifier RABBIT_BLACK_LOCATION = Identifier.withDefaultNamespace("textures/entity/rabbit/black.png");
   private static final Identifier RABBIT_GOLD_LOCATION = Identifier.withDefaultNamespace("textures/entity/rabbit/gold.png");
   private static final Identifier RABBIT_SALT_LOCATION = Identifier.withDefaultNamespace("textures/entity/rabbit/salt.png");
   private static final Identifier RABBIT_WHITE_SPLOTCHED_LOCATION = Identifier.withDefaultNamespace("textures/entity/rabbit/white_splotched.png");
   private static final Identifier RABBIT_TOAST_LOCATION = Identifier.withDefaultNamespace("textures/entity/rabbit/toast.png");
   private static final Identifier RABBIT_EVIL_LOCATION = Identifier.withDefaultNamespace("textures/entity/rabbit/caerbannog.png");

   public RabbitRenderer(final EntityRendererProvider.Context context) {
      super(context, new RabbitModel(context.bakeLayer(ModelLayers.RABBIT)), new RabbitModel(context.bakeLayer(ModelLayers.RABBIT_BABY)), 0.3F);
   }

   public Identifier getTextureLocation(final RabbitRenderState state) {
      if (state.isToast) {
         return RABBIT_TOAST_LOCATION;
      }

      return switch (state.variant) {
         case BROWN -> RABBIT_BROWN_LOCATION;
         case WHITE -> RABBIT_WHITE_LOCATION;
         case BLACK -> RABBIT_BLACK_LOCATION;
         case GOLD -> RABBIT_GOLD_LOCATION;
         case SALT -> RABBIT_SALT_LOCATION;
         case WHITE_SPLOTCHED -> RABBIT_WHITE_SPLOTCHED_LOCATION;
         case EVIL -> RABBIT_EVIL_LOCATION;
      };
   }

   public RabbitRenderState createRenderState() {
      return new RabbitRenderState();
   }

   public void extractRenderState(final Rabbit entity, final RabbitRenderState state, final float partialTicks) {
      super.extractRenderState(entity, state, partialTicks);
      state.jumpCompletion = entity.getJumpCompletion(partialTicks);
      state.isToast = checkMagicName(entity, "Toast");
      state.variant = entity.getVariant();
   }
}
