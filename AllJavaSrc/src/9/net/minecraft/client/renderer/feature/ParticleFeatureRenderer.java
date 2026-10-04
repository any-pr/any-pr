package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import org.jspecify.annotations.Nullable;

public class ParticleFeatureRenderer implements AutoCloseable {
   private final Queue<ParticleFeatureRenderer.ParticleBufferCache> availableBuffers = new ArrayDeque<>();
   private final List<ParticleFeatureRenderer.ParticleBufferCache> usedBuffers = new ArrayList<>();

   public void render(final SubmitNodeCollection nodeCollection) {
      if (!nodeCollection.getParticleGroupRenderers().isEmpty()) {
         GpuDevice device = RenderSystem.getDevice();
         Minecraft minecraft = Minecraft.getInstance();
         TextureManager textureManager = minecraft.getTextureManager();
         RenderTarget mainTarget = minecraft.getMainRenderTarget();
         RenderTarget particleTarget = minecraft.levelRenderer.getParticlesTarget();

         for (SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer : nodeCollection.getParticleGroupRenderers()) {
            ParticleFeatureRenderer.ParticleBufferCache buffer = this.availableBuffers.poll();
            if (buffer == null) {
               buffer = new ParticleFeatureRenderer.ParticleBufferCache();
            }

            this.usedBuffers.add(buffer);
            QuadParticleRenderState.PreparedBuffers prepared = particleGroupRenderer.prepare(buffer);
            if (prepared != null) {
               RenderPass renderPass = device.createCommandEncoder()
                  .createRenderPass(
                     () -> "Particles - Main", mainTarget.getColorTextureView(), OptionalInt.empty(), mainTarget.getDepthTextureView(), OptionalDouble.empty()
                  );

               try {
                  this.prepareRenderPass(renderPass);
                  particleGroupRenderer.render(prepared, buffer, renderPass, textureManager, false);
                  if (particleTarget == null) {
                     particleGroupRenderer.render(prepared, buffer, renderPass, textureManager, true);
                  }
               } catch (Throwable var17) {
                  if (renderPass != null) {
                     try {
                        renderPass.close();
                     } catch (Throwable var15) {
                        var17.addSuppressed(var15);
                     }
                  }

                  throw var17;
               }

               if (renderPass != null) {
                  renderPass.close();
               }

               if (particleTarget != null) {
                  renderPass = device.createCommandEncoder()
                     .createRenderPass(
                        () -> "Particles - Transparent",
                        particleTarget.getColorTextureView(),
                        OptionalInt.empty(),
                        particleTarget.getDepthTextureView(),
                        OptionalDouble.empty()
                     );

                  try {
                     this.prepareRenderPass(renderPass);
                     particleGroupRenderer.render(prepared, buffer, renderPass, textureManager, true);
                  } catch (Throwable var16) {
                     if (renderPass != null) {
                        try {
                           renderPass.close();
                        } catch (Throwable var14) {
                           var16.addSuppressed(var14);
                        }
                     }

                     throw var16;
                  }

                  if (renderPass != null) {
                     renderPass.close();
                  }
               }
            }
         }
      }
   }

   public void endFrame() {
      for (ParticleFeatureRenderer.ParticleBufferCache usedBuffer : this.usedBuffers) {
         usedBuffer.rotate();
      }

      this.availableBuffers.addAll(this.usedBuffers);
      this.usedBuffers.clear();
   }

   private void prepareRenderPass(final RenderPass renderPass) {
      renderPass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
      renderPass.setUniform("Fog", RenderSystem.getShaderFog());
      renderPass.bindTexture(
         "Sampler2", Minecraft.getInstance().gameRenderer.lightTexture().getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
      );
   }

   @Override
   public void close() {
      this.availableBuffers.forEach(ParticleFeatureRenderer.ParticleBufferCache::close);
   }

   public static class ParticleBufferCache implements AutoCloseable {
      private @Nullable MappableRingBuffer ringBuffer;

      public void write(final ByteBuffer byteBuffer) {
         if (this.ringBuffer == null || this.ringBuffer.size() < byteBuffer.remaining()) {
            if (this.ringBuffer != null) {
               this.ringBuffer.close();
            }

            this.ringBuffer = new MappableRingBuffer(() -> "Particle Vertices", 34, byteBuffer.remaining());
         }

         MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.ringBuffer.currentBuffer().slice(), false, true);

         try {
            view.data().put(byteBuffer);
         } catch (Throwable var6) {
            if (view != null) {
               try {
                  view.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (view != null) {
            view.close();
         }
      }

      public GpuBuffer get() {
         if (this.ringBuffer == null) {
            throw new IllegalStateException("Can't get buffer before it's made");
         } else {
            return this.ringBuffer.currentBuffer();
         }
      }

      void rotate() {
         if (this.ringBuffer != null) {
            this.ringBuffer.rotate();
         }
      }

      @Override
      public void close() {
         if (this.ringBuffer != null) {
            this.ringBuffer.close();
         }
      }
   }
}
