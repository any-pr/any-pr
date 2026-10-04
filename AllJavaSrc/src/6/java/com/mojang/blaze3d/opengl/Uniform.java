package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.textures.TextureFormat;

public sealed interface Uniform extends AutoCloseable permits Uniform.Ubo, Uniform.Utb, Uniform.Sampler {
   @Override
   default void close() {
   }

   record Sampler(int location, int samplerIndex) implements Uniform {
   }

   record Ubo(int blockBinding) implements Uniform {
   }

   record Utb(int location, int samplerIndex, TextureFormat format, int texture) implements Uniform {
      public Utb(final int location, final int samplerIndex, final TextureFormat format) {
         this(location, samplerIndex, format, GlStateManager._genTexture());
      }

      @Override
      public void close() {
         GlStateManager._deleteTexture(this.texture);
      }
   }
}
