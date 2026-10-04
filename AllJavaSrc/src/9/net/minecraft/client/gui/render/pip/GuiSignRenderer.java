package net.minecraft.client.gui.render.pip;

import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.state.pip.GuiSignRenderState;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;

public class GuiSignRenderer extends PictureInPictureRenderer<GuiSignRenderState> {
   private final MaterialSet materials;

   public GuiSignRenderer(final MultiBufferSource.BufferSource bufferSource, final MaterialSet materials) {
      super(bufferSource);
      this.materials = materials;
   }

   @Override
   public Class<GuiSignRenderState> getRenderStateClass() {
      return GuiSignRenderState.class;
   }

   protected void renderToTexture(final GuiSignRenderState renderState, final PoseStack poseStack) {
      Minecraft.getInstance().gameRenderer.getLighting().setupFor(Entry.ITEMS_FLAT);
      poseStack.translate(0.0F, -0.75F, 0.0F);
      Material material = Sheets.getSignMaterial(renderState.woodType());
      Model.Simple model = renderState.signModel();
      VertexConsumer buffer = material.buffer(this.materials, this.bufferSource, model::renderType);
      model.renderToBuffer(poseStack, buffer, 15728880, OverlayTexture.NO_OVERLAY);
   }

   @Override
   protected String getTextureLabel() {
      return "sign";
   }
}
