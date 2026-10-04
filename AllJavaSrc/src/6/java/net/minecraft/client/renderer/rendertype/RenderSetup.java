package net.minecraft.client.renderer.rendertype;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class RenderSetup {
   final RenderPipeline pipeline;
   final Map<String, RenderSetup.TextureBinding> textures;
   final TextureTransform textureTransform;
   final OutputTarget outputTarget;
   final RenderSetup.OutlineProperty outlineProperty;
   final boolean useLightmap;
   final boolean useOverlay;
   final boolean affectsCrumbling;
   final boolean sortOnUpload;
   final int bufferSize;
   final LayeringTransform layeringTransform;

   private RenderSetup(
      final RenderPipeline pipeline,
      final Map<String, RenderSetup.TextureBinding> textures,
      final boolean useLightmap,
      final boolean useOverlay,
      final LayeringTransform layeringTransform,
      final OutputTarget outputTarget,
      final TextureTransform textureTransform,
      final RenderSetup.OutlineProperty outlineProperty,
      final boolean affectsCrumbling,
      final boolean sortOnUpload,
      final int bufferSize
   ) {
      this.pipeline = pipeline;
      this.textures = textures;
      this.outputTarget = outputTarget;
      this.textureTransform = textureTransform;
      this.useLightmap = useLightmap;
      this.useOverlay = useOverlay;
      this.outlineProperty = outlineProperty;
      this.layeringTransform = layeringTransform;
      this.affectsCrumbling = affectsCrumbling;
      this.sortOnUpload = sortOnUpload;
      this.bufferSize = bufferSize;
   }

   @Override
   public String toString() {
      return "RenderSetup[layeringTransform="
         + this.layeringTransform
         + ", textureTransform="
         + this.textureTransform
         + ", textures="
         + this.textures
         + ", outlineProperty="
         + this.outlineProperty
         + ", useLightmap="
         + this.useLightmap
         + ", useOverlay="
         + this.useOverlay
         + "]";
   }

   public static RenderSetup.RenderSetupBuilder builder(final RenderPipeline pipeline) {
      return new RenderSetup.RenderSetupBuilder(pipeline);
   }

   public Map<String, RenderSetup.TextureAndSampler> getTextures() {
      if (this.textures.isEmpty() && !this.useOverlay && !this.useLightmap) {
         return Collections.emptyMap();
      }

      Map<String, RenderSetup.TextureAndSampler> result = new HashMap<>();
      if (this.useOverlay) {
         result.put(
            "Sampler1",
            new RenderSetup.TextureAndSampler(
               Minecraft.getInstance().gameRenderer.overlayTexture().getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            )
         );
      }

      if (this.useLightmap) {
         result.put(
            "Sampler2",
            new RenderSetup.TextureAndSampler(
               Minecraft.getInstance().gameRenderer.lightTexture().getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            )
         );
      }

      TextureManager textureManager = Minecraft.getInstance().getTextureManager();

      for (Entry<String, RenderSetup.TextureBinding> entry : this.textures.entrySet()) {
         AbstractTexture texture = textureManager.getTexture(entry.getValue().location);
         GpuSampler samplerOverride = entry.getValue().sampler().get();
         result.put(
            entry.getKey(), new RenderSetup.TextureAndSampler(texture.getTextureView(), samplerOverride != null ? samplerOverride : texture.getSampler())
         );
      }

      return result;
   }

   public enum OutlineProperty {
      NONE("none"),
      IS_OUTLINE("is_outline"),
      AFFECTS_OUTLINE("affects_outline");

      private final String name;

      OutlineProperty(final String name) {
         this.name = name;
      }

      @Override
      public String toString() {
         return this.name;
      }
   }

   public static class RenderSetupBuilder {
      private final RenderPipeline pipeline;
      private boolean useLightmap = false;
      private boolean useOverlay = false;
      private LayeringTransform layeringTransform = LayeringTransform.NO_LAYERING;
      private OutputTarget outputTarget = OutputTarget.MAIN_TARGET;
      private TextureTransform textureTransform = TextureTransform.DEFAULT_TEXTURING;
      private boolean affectsCrumbling = false;
      private boolean sortOnUpload = false;
      private int bufferSize = 1536;
      private RenderSetup.OutlineProperty outlineProperty = RenderSetup.OutlineProperty.NONE;
      private final Map<String, RenderSetup.TextureBinding> textures = new HashMap<>();

      private RenderSetupBuilder(final RenderPipeline pipeline) {
         this.pipeline = pipeline;
      }

      public RenderSetup.RenderSetupBuilder withTexture(final String name, final Identifier texture) {
         this.textures.put(name, new RenderSetup.TextureBinding(texture, () -> null));
         return this;
      }

      public RenderSetup.RenderSetupBuilder withTexture(final String name, final Identifier texture, final @Nullable Supplier<GpuSampler> sampler) {
         this.textures.put(name, new RenderSetup.TextureBinding(texture, Suppliers.memoize(() -> sampler == null ? null : sampler.get())));
         return this;
      }

      public RenderSetup.RenderSetupBuilder useLightmap() {
         this.useLightmap = true;
         return this;
      }

      public RenderSetup.RenderSetupBuilder useOverlay() {
         this.useOverlay = true;
         return this;
      }

      public RenderSetup.RenderSetupBuilder affectsCrumbling() {
         this.affectsCrumbling = true;
         return this;
      }

      public RenderSetup.RenderSetupBuilder sortOnUpload() {
         this.sortOnUpload = true;
         return this;
      }

      public RenderSetup.RenderSetupBuilder bufferSize(final int bufferSize) {
         this.bufferSize = bufferSize;
         return this;
      }

      public RenderSetup.RenderSetupBuilder setLayeringTransform(final LayeringTransform layeringTransform) {
         this.layeringTransform = layeringTransform;
         return this;
      }

      public RenderSetup.RenderSetupBuilder setOutputTarget(final OutputTarget outputTarget) {
         this.outputTarget = outputTarget;
         return this;
      }

      public RenderSetup.RenderSetupBuilder setTextureTransform(final TextureTransform textureTransform) {
         this.textureTransform = textureTransform;
         return this;
      }

      public RenderSetup.RenderSetupBuilder setOutline(final RenderSetup.OutlineProperty outlineProperty) {
         this.outlineProperty = outlineProperty;
         return this;
      }

      public RenderSetup createRenderSetup() {
         return new RenderSetup(
            this.pipeline,
            this.textures,
            this.useLightmap,
            this.useOverlay,
            this.layeringTransform,
            this.outputTarget,
            this.textureTransform,
            this.outlineProperty,
            this.affectsCrumbling,
            this.sortOnUpload,
            this.bufferSize
         );
      }
   }

   public record TextureAndSampler(GpuTextureView textureView, GpuSampler sampler) {
   }

   record TextureBinding(Identifier location, Supplier<@Nullable GpuSampler> sampler) {
   }
}
