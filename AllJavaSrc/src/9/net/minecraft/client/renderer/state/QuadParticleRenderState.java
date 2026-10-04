package net.minecraft.client.renderer.state;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ParticleFeatureRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public class QuadParticleRenderState implements ParticleGroupRenderState, SubmitNodeCollector.ParticleGroupRenderer {
   private static final int INITIAL_PARTICLE_CAPACITY = 1024;
   private static final int FLOATS_PER_PARTICLE = 12;
   private static final int INTS_PER_PARTICLE = 2;
   private final Map<SingleQuadParticle.Layer, QuadParticleRenderState.Storage> particles = new HashMap<>();
   private int particleCount;

   public void add(
      final SingleQuadParticle.Layer layer,
      final float x,
      final float y,
      final float z,
      final float xRot,
      final float yRot,
      final float zRot,
      final float wRot,
      final float scale,
      final float u0,
      final float u1,
      final float v0,
      final float v1,
      final int color,
      final int lightColor
   ) {
      this.particles
         .computeIfAbsent(layer, ignored -> new QuadParticleRenderState.Storage())
         .add(x, y, z, xRot, yRot, zRot, wRot, scale, u0, u1, v0, v1, color, lightColor);
      this.particleCount++;
   }

   @Override
   public void clear() {
      this.particles.values().forEach(QuadParticleRenderState.Storage::clear);
      this.particleCount = 0;
   }

   @Override
   public QuadParticleRenderState.@Nullable PreparedBuffers prepare(final ParticleFeatureRenderer.ParticleBufferCache cachedBuffer) {
      int vertexCount = this.particleCount * 4;
      ByteBufferBuilder builder = ByteBufferBuilder.exactlySized(vertexCount * DefaultVertexFormat.PARTICLE.getVertexSize());

      QuadParticleRenderState.PreparedBuffers var9;
      label56: {
         Object var13;
         try {
            BufferBuilder bufferBuilder = new BufferBuilder(builder, Mode.QUADS, DefaultVertexFormat.PARTICLE);
            Map<SingleQuadParticle.Layer, QuadParticleRenderState.PreparedLayer> preparedLayers = new HashMap<>();
            int offset = 0;

            for (Entry<SingleQuadParticle.Layer, QuadParticleRenderState.Storage> entry : this.particles.entrySet()) {
               entry.getValue()
                  .forEachParticle(
                     (x, y, z, xRot, yRot, zRot, wRot, scale, u0, u1, v0, v1, color, lightColor) -> this.renderRotatedQuad(
                        bufferBuilder, x, y, z, xRot, yRot, zRot, wRot, scale, u0, u1, v0, v1, color, lightColor
                     )
                  );
               if (entry.getValue().count() > 0) {
                  preparedLayers.put(entry.getKey(), new QuadParticleRenderState.PreparedLayer(offset, entry.getValue().count() * 6));
               }

               offset += entry.getValue().count() * 4;
            }

            MeshData mesh = bufferBuilder.build();
            if (mesh != null) {
               cachedBuffer.write(mesh.vertexBuffer());
               RenderSystem.getSequentialBuffer(Mode.QUADS).getBuffer(mesh.drawState().indexCount());
               GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                  .writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
               var9 = new QuadParticleRenderState.PreparedBuffers(mesh.drawState().indexCount(), dynamicTransforms, preparedLayers);
               break label56;
            }

            var13 = null;
         } catch (Throwable var11) {
            if (builder != null) {
               try {
                  builder.close();
               } catch (Throwable var10) {
                  var11.addSuppressed(var10);
               }
            }

            throw var11;
         }

         if (builder != null) {
            builder.close();
         }

         return (QuadParticleRenderState.PreparedBuffers)var13;
      }

      if (builder != null) {
         builder.close();
      }

      return var9;
   }

   @Override
   public void render(
      final QuadParticleRenderState.PreparedBuffers preparedBuffers,
      final ParticleFeatureRenderer.ParticleBufferCache bufferCache,
      final RenderPass renderPass,
      final TextureManager textureManager,
      final boolean isTransparency
   ) {
      AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(Mode.QUADS);
      renderPass.setVertexBuffer(0, bufferCache.get());
      renderPass.setIndexBuffer(indexBuffer.getBuffer(preparedBuffers.indexCount), indexBuffer.type());
      renderPass.setUniform("DynamicTransforms", preparedBuffers.dynamicTransforms);

      for (Entry<SingleQuadParticle.Layer, QuadParticleRenderState.PreparedLayer> entry : preparedBuffers.layers.entrySet()) {
         if (isTransparency == entry.getKey().translucent()) {
            renderPass.setPipeline(entry.getKey().pipeline());
            AbstractTexture texture = textureManager.getTexture(entry.getKey().textureAtlasLocation());
            renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
            renderPass.drawIndexed(entry.getValue().vertexOffset, 0, entry.getValue().indexCount, 1);
         }
      }
   }

   protected void renderRotatedQuad(
      final VertexConsumer builder,
      final float x,
      final float y,
      final float z,
      final float xRot,
      final float yRot,
      final float zRot,
      final float wRot,
      final float scale,
      final float u0,
      final float u1,
      final float v0,
      final float v1,
      final int color,
      final int lightColor
   ) {
      Quaternionf rotation = new Quaternionf(xRot, yRot, zRot, wRot);
      this.renderVertex(builder, rotation, x, y, z, 1.0F, -1.0F, scale, u1, v1, color, lightColor);
      this.renderVertex(builder, rotation, x, y, z, 1.0F, 1.0F, scale, u1, v0, color, lightColor);
      this.renderVertex(builder, rotation, x, y, z, -1.0F, 1.0F, scale, u0, v0, color, lightColor);
      this.renderVertex(builder, rotation, x, y, z, -1.0F, -1.0F, scale, u0, v1, color, lightColor);
   }

   private void renderVertex(
      final VertexConsumer builder,
      final Quaternionf rotation,
      final float x,
      final float y,
      final float z,
      final float nx,
      final float ny,
      final float scale,
      final float u,
      final float v,
      final int color,
      final int lightColor
   ) {
      Vector3f scratch = new Vector3f(nx, ny, 0.0F).rotate(rotation).mul(scale).add(x, y, z);
      builder.addVertex(scratch.x(), scratch.y(), scratch.z()).setUv(u, v).setColor(color).setLight(lightColor);
   }

   @Override
   public void submit(final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
      if (this.particleCount > 0) {
         submitNodeCollector.submitParticleGroup(this);
      }
   }

   @FunctionalInterface
   public interface ParticleConsumer {
      void consume(
         final float x,
         final float y,
         final float z,
         final float xRot,
         final float yRot,
         final float zRot,
         final float wRot,
         final float scale,
         final float u0,
         final float u1,
         final float v0,
         final float v1,
         final int color,
         final int lightColor
      );
   }

   public record PreparedBuffers(int indexCount, GpuBufferSlice dynamicTransforms, Map<SingleQuadParticle.Layer, QuadParticleRenderState.PreparedLayer> layers) {
   }

   public record PreparedLayer(int vertexOffset, int indexCount) {
   }

   private static class Storage {
      private int capacity = 1024;
      private float[] floatValues = new float[12288];
      private int[] intValues = new int[2048];
      private int currentParticleIndex;

      public void add(
         final float x,
         final float y,
         final float z,
         final float xRot,
         final float yRot,
         final float zRot,
         final float wRot,
         final float scale,
         final float u0,
         final float u1,
         final float v0,
         final float v1,
         final int color,
         final int lightColor
      ) {
         if (this.currentParticleIndex >= this.capacity) {
            this.grow();
         }

         int index = this.currentParticleIndex * 12;
         this.floatValues[index++] = x;
         this.floatValues[index++] = y;
         this.floatValues[index++] = z;
         this.floatValues[index++] = xRot;
         this.floatValues[index++] = yRot;
         this.floatValues[index++] = zRot;
         this.floatValues[index++] = wRot;
         this.floatValues[index++] = scale;
         this.floatValues[index++] = u0;
         this.floatValues[index++] = u1;
         this.floatValues[index++] = v0;
         this.floatValues[index] = v1;
         index = this.currentParticleIndex * 2;
         this.intValues[index++] = color;
         this.intValues[index] = lightColor;
         this.currentParticleIndex++;
      }

      public void forEachParticle(final QuadParticleRenderState.ParticleConsumer consumer) {
         for (int particleIndex = 0; particleIndex < this.currentParticleIndex; particleIndex++) {
            int floatIndex = particleIndex * 12;
            int intIndex = particleIndex * 2;
            consumer.consume(
               this.floatValues[floatIndex++],
               this.floatValues[floatIndex++],
               this.floatValues[floatIndex++],
               this.floatValues[floatIndex++],
               this.floatValues[floatIndex++],
               this.floatValues[floatIndex++],
               this.floatValues[floatIndex++],
               this.floatValues[floatIndex++],
               this.floatValues[floatIndex++],
               this.floatValues[floatIndex++],
               this.floatValues[floatIndex++],
               this.floatValues[floatIndex],
               this.intValues[intIndex++],
               this.intValues[intIndex]
            );
         }
      }

      public void clear() {
         this.currentParticleIndex = 0;
      }

      private void grow() {
         this.capacity *= 2;
         this.floatValues = Arrays.copyOf(this.floatValues, this.capacity * 12);
         this.intValues = Arrays.copyOf(this.intValues, this.capacity * 2);
      }

      public int count() {
         return this.currentParticleIndex;
      }
   }
}
