package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.animal.axolotl.AxolotlModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.AxolotlRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.animal.axolotl.Axolotl;

public class AxolotlRenderer extends AgeableMobRenderer<Axolotl, AxolotlRenderState, AxolotlModel> {
   private static final Map<Axolotl.Variant, Identifier> TEXTURE_BY_TYPE = Util.make(Maps.newHashMap(), map -> {
      for (Axolotl.Variant variant : Axolotl.Variant.values()) {
         map.put(variant, Identifier.withDefaultNamespace(String.format(Locale.ROOT, "textures/entity/axolotl/axolotl_%s.png", variant.getName())));
      }
   });

   public AxolotlRenderer(final EntityRendererProvider.Context context) {
      super(context, new AxolotlModel(context.bakeLayer(ModelLayers.AXOLOTL)), new AxolotlModel(context.bakeLayer(ModelLayers.AXOLOTL_BABY)), 0.5F);
   }

   public Identifier getTextureLocation(final AxolotlRenderState state) {
      return TEXTURE_BY_TYPE.get(state.variant);
   }

   public AxolotlRenderState createRenderState() {
      return new AxolotlRenderState();
   }

   public void extractRenderState(final Axolotl entity, final AxolotlRenderState state, final float partialTicks) {
      super.extractRenderState(entity, state, partialTicks);
      state.variant = entity.getVariant();
      state.playingDeadFactor = entity.playingDeadAnimator.getFactor(partialTicks);
      state.inWaterFactor = entity.inWaterAnimator.getFactor(partialTicks);
      state.onGroundFactor = entity.onGroundAnimator.getFactor(partialTicks);
      state.movingFactor = entity.movingAnimator.getFactor(partialTicks);
   }
}
