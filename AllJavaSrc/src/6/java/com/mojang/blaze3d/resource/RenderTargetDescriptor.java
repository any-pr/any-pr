package com.mojang.blaze3d.resource;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;

public record RenderTargetDescriptor(int width, int height, boolean useDepth, int clearColor) implements ResourceDescriptor<RenderTarget> {
   public RenderTarget allocate() {
      return new TextureTarget(null, this.width, this.height, this.useDepth);
   }

   public void prepare(final RenderTarget resource) {
      if (this.useDepth) {
         RenderSystem.getDevice()
            .createCommandEncoder()
            .clearColorAndDepthTextures(resource.getColorTexture(), this.clearColor, resource.getDepthTexture(), 1.0);
      } else {
         RenderSystem.getDevice().createCommandEncoder().clearColorTexture(resource.getColorTexture(), this.clearColor);
      }
   }

   public void free(final RenderTarget resource) {
      resource.destroyBuffers();
   }

   @Override
   public boolean canUsePhysicalResource(final ResourceDescriptor<?> other) {
      return !(other instanceof RenderTargetDescriptor descriptor)
         ? false
         : this.width == descriptor.width && this.height == descriptor.height && this.useDepth == descriptor.useDepth;
   }
}
