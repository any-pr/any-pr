package com.mojang.blaze3d.textures;

public enum TextureFormat {
   RGBA8(4),
   RED8(1),
   RED8I(1),
   DEPTH32(4);

   private final int pixelSize;

   TextureFormat(final int pixelSize) {
      this.pixelSize = pixelSize;
   }

   public int pixelSize() {
      return this.pixelSize;
   }

   public boolean hasColorAspect() {
      return this == RGBA8 || this == RED8;
   }

   public boolean hasDepthAspect() {
      return this == DEPTH32;
   }
}
