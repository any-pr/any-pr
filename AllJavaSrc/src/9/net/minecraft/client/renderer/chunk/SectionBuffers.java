package net.minecraft.client.renderer.chunk;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import org.jspecify.annotations.Nullable;

public final class SectionBuffers implements AutoCloseable {
   private GpuBuffer vertexBuffer;
   private @Nullable GpuBuffer indexBuffer;
   private int indexCount;
   private IndexType indexType;

   public SectionBuffers(final GpuBuffer vertexBuffer, final @Nullable GpuBuffer indexBuffer, final int indexCount, final IndexType indexType) {
      this.vertexBuffer = vertexBuffer;
      this.indexBuffer = indexBuffer;
      this.indexCount = indexCount;
      this.indexType = indexType;
   }

   public GpuBuffer getVertexBuffer() {
      return this.vertexBuffer;
   }

   public @Nullable GpuBuffer getIndexBuffer() {
      return this.indexBuffer;
   }

   public void setIndexBuffer(final @Nullable GpuBuffer indexBuffer) {
      this.indexBuffer = indexBuffer;
   }

   public int getIndexCount() {
      return this.indexCount;
   }

   public IndexType getIndexType() {
      return this.indexType;
   }

   public void setIndexType(final IndexType indexType) {
      this.indexType = indexType;
   }

   public void setIndexCount(final int indexCount) {
      this.indexCount = indexCount;
   }

   public void setVertexBuffer(final GpuBuffer vertexBuffer) {
      this.vertexBuffer = vertexBuffer;
   }

   @Override
   public void close() {
      this.vertexBuffer.close();
      if (this.indexBuffer != null) {
         this.indexBuffer.close();
      }
   }
}
