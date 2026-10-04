package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import org.jspecify.annotations.Nullable;

public class GlRenderPass implements RenderPass {
   protected static final int MAX_VERTEX_BUFFERS = 1;
   public static final boolean VALIDATION = SharedConstants.IS_RUNNING_IN_IDE;
   private final GlCommandEncoder encoder;
   private final boolean hasDepthTexture;
   private boolean closed;
   protected @Nullable GlRenderPipeline pipeline;
   protected final @Nullable GpuBuffer[] vertexBuffers = new GpuBuffer[1];
   protected @Nullable GpuBuffer indexBuffer;
   protected VertexFormat.IndexType indexType = VertexFormat.IndexType.INT;
   private final ScissorState scissorState = new ScissorState();
   protected final HashMap<String, GpuBufferSlice> uniforms = new HashMap<>();
   protected final HashMap<String, GlRenderPass.TextureViewAndSampler> samplers = new HashMap<>();
   protected final Set<String> dirtyUniforms = new HashSet<>();
   protected int pushedDebugGroups;

   public GlRenderPass(final GlCommandEncoder encoder, final boolean hasDepthTexture) {
      this.encoder = encoder;
      this.hasDepthTexture = hasDepthTexture;
   }

   public boolean hasDepthTexture() {
      return this.hasDepthTexture;
   }

   @Override
   public void pushDebugGroup(final Supplier<String> label) {
      if (this.closed) {
         throw new IllegalStateException("Can't use a closed render pass");
      }

      this.pushedDebugGroups++;
      this.encoder.getDevice().debugLabels().pushDebugGroup(label);
   }

   @Override
   public void popDebugGroup() {
      if (this.closed) {
         throw new IllegalStateException("Can't use a closed render pass");
      }

      if (this.pushedDebugGroups == 0) {
         throw new IllegalStateException("Can't pop more debug groups than was pushed!");
      }

      this.pushedDebugGroups--;
      this.encoder.getDevice().debugLabels().popDebugGroup();
   }

   @Override
   public void setPipeline(final RenderPipeline pipeline) {
      if (this.pipeline == null || this.pipeline.info() != pipeline) {
         this.dirtyUniforms.addAll(this.uniforms.keySet());
         this.dirtyUniforms.addAll(this.samplers.keySet());
      }

      this.pipeline = this.encoder.getDevice().getOrCompilePipeline(pipeline);
   }

   @Override
   public void bindTexture(final String name, final @Nullable GpuTextureView textureView, final @Nullable GpuSampler sampler) {
      if (sampler == null) {
         this.samplers.remove(name);
      } else {
         this.samplers.put(name, new GlRenderPass.TextureViewAndSampler((GlTextureView)textureView, (GlSampler)sampler));
      }

      this.dirtyUniforms.add(name);
   }

   @Override
   public void setUniform(final String name, final GpuBuffer value) {
      this.uniforms.put(name, value.slice());
      this.dirtyUniforms.add(name);
   }

   @Override
   public void setUniform(final String name, final GpuBufferSlice value) {
      int alignment = this.encoder.getDevice().getUniformOffsetAlignment();
      if (value.offset() % alignment > 0L) {
         throw new IllegalArgumentException("Uniform buffer offset must be aligned to " + alignment);
      }

      this.uniforms.put(name, value);
      this.dirtyUniforms.add(name);
   }

   @Override
   public void enableScissor(final int x, final int y, final int width, final int height) {
      this.scissorState.enable(x, y, width, height);
   }

   @Override
   public void disableScissor() {
      this.scissorState.disable();
   }

   public boolean isScissorEnabled() {
      return this.scissorState.enabled();
   }

   public int getScissorX() {
      return this.scissorState.x();
   }

   public int getScissorY() {
      return this.scissorState.y();
   }

   public int getScissorWidth() {
      return this.scissorState.width();
   }

   public int getScissorHeight() {
      return this.scissorState.height();
   }

   @Override
   public void setVertexBuffer(final int slot, final GpuBuffer vertexBuffer) {
      if (slot >= 0 && slot < 1) {
         this.vertexBuffers[slot] = vertexBuffer;
      } else {
         throw new IllegalArgumentException("Vertex buffer slot is out of range: " + slot);
      }
   }

   @Override
   public void setIndexBuffer(final @Nullable GpuBuffer indexBuffer, final VertexFormat.IndexType indexType) {
      this.indexBuffer = indexBuffer;
      this.indexType = indexType;
   }

   @Override
   public void drawIndexed(final int baseVertex, final int firstIndex, final int indexCount, final int instanceCount) {
      if (this.closed) {
         throw new IllegalStateException("Can't use a closed render pass");
      }

      this.encoder.executeDraw(this, baseVertex, firstIndex, indexCount, this.indexType, instanceCount);
   }

   @Override
   public <T> void drawMultipleIndexed(
      final Collection<RenderPass.Draw<T>> draws,
      final @Nullable GpuBuffer defaultIndexBuffer,
      final VertexFormat.@Nullable IndexType defaultIndexType,
      final Collection<String> dynamicUniforms,
      final T uniformArgument
   ) {
      if (this.closed) {
         throw new IllegalStateException("Can't use a closed render pass");
      }

      this.encoder.executeDrawMultiple(this, draws, defaultIndexBuffer, defaultIndexType, dynamicUniforms, uniformArgument);
   }

   @Override
   public void draw(final int firstVertex, final int vertexCount) {
      if (this.closed) {
         throw new IllegalStateException("Can't use a closed render pass");
      }

      this.encoder.executeDraw(this, firstVertex, 0, vertexCount, null, 1);
   }

   @Override
   public void close() {
      if (!this.closed) {
         if (this.pushedDebugGroups > 0) {
            throw new IllegalStateException("Render pass had debug groups left open!");
         }

         this.closed = true;
         this.encoder.finishRenderPass();
      }
   }

   protected record TextureViewAndSampler(GlTextureView view, GlSampler sampler) {
   }
}
