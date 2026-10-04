package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.animal.llama.LlamaModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.layers.LlamaDecorLayer;
import net.minecraft.client.renderer.entity.state.LlamaRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.equine.Llama;

public class LlamaRenderer extends AgeableMobRenderer<Llama, LlamaRenderState, LlamaModel> {
   private static final Identifier CREAMY = Identifier.withDefaultNamespace("textures/entity/llama/creamy.png");
   private static final Identifier WHITE = Identifier.withDefaultNamespace("textures/entity/llama/white.png");
   private static final Identifier BROWN = Identifier.withDefaultNamespace("textures/entity/llama/brown.png");
   private static final Identifier GRAY = Identifier.withDefaultNamespace("textures/entity/llama/gray.png");

   public LlamaRenderer(final EntityRendererProvider.Context context, final ModelLayerLocation model, final ModelLayerLocation babyModel) {
      super(context, new LlamaModel(context.bakeLayer(model)), new LlamaModel(context.bakeLayer(babyModel)), 0.7F);
      this.addLayer(new LlamaDecorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));
   }

   public Identifier getTextureLocation(final LlamaRenderState state) {
      return switch (state.variant) {
         case CREAMY -> CREAMY;
         case WHITE -> WHITE;
         case BROWN -> BROWN;
         case GRAY -> GRAY;
      };
   }

   public LlamaRenderState createRenderState() {
      return new LlamaRenderState();
   }

   public void extractRenderState(final Llama entity, final LlamaRenderState state, final float partialTicks) {
      super.extractRenderState(entity, state, partialTicks);
      state.variant = entity.getVariant();
      state.hasChest = !entity.isBaby() && entity.hasChest();
      state.bodyItem = entity.getBodyArmorItem();
      state.isTraderLlama = entity.isTraderLlama();
   }
}
