package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.GraphicsWorkarounds;
import com.mojang.blaze3d.buffers.GpuBuffer;
import java.nio.ByteBuffer;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GLCapabilities;

public abstract class DirectStateAccess {
   public static DirectStateAccess create(final GLCapabilities capabilities, final Set<String> enabledExtensions, final GraphicsWorkarounds workarounds) {
      if (capabilities.GL_ARB_direct_state_access && GlDevice.USE_GL_ARB_direct_state_access && !workarounds.isGlOnDx12()) {
         enabledExtensions.add("GL_ARB_direct_state_access");
         return new DirectStateAccess.Core();
      } else {
         return new DirectStateAccess.Emulated();
      }
   }

   abstract int createBuffer();

   abstract void bufferData(int buffer, long size, @GpuBuffer.Usage int usage);

   abstract void bufferData(int buffer, ByteBuffer data, @GpuBuffer.Usage int usage);

   abstract void bufferSubData(int buffer, long offset, ByteBuffer data, @GpuBuffer.Usage int usage);

   abstract void bufferStorage(int buffer, long size, @GpuBuffer.Usage int usage);

   abstract void bufferStorage(int buffer, ByteBuffer data, @GpuBuffer.Usage int usage);

   abstract @Nullable ByteBuffer mapBufferRange(int buffer, long offset, long length, int access, @GpuBuffer.Usage int usage);

   abstract void unmapBuffer(int buffer, @GpuBuffer.Usage int usage);

   abstract int createFrameBufferObject();

   abstract void bindFrameBufferTextures(int fbo, int color0, int depth, int mipLevel, int bindSlot);

   abstract void blitFrameBuffers(
      int source, int dest, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter
   );

   abstract void flushMappedBufferRange(final int handle, final long offset, final long length, @GpuBuffer.Usage final int usage);

   abstract void copyBufferSubData(int source, int target, long sourceOffset, long targetOffset, long length);

   private static class Core extends DirectStateAccess {
      @Override
      int createBuffer() {
         GlStateManager.incrementTrackedBuffers();
         return ARBDirectStateAccess.glCreateBuffers();
      }

      @Override
      void bufferData(final int buffer, final long size, @GpuBuffer.Usage final int usage) {
         ARBDirectStateAccess.glNamedBufferData(buffer, size, GlConst.bufferUsageToGlEnum(usage));
      }

      @Override
      void bufferData(final int buffer, final ByteBuffer data, @GpuBuffer.Usage final int usage) {
         ARBDirectStateAccess.glNamedBufferData(buffer, data, GlConst.bufferUsageToGlEnum(usage));
      }

      @Override
      void bufferSubData(final int buffer, final long offset, final ByteBuffer data, @GpuBuffer.Usage final int usage) {
         ARBDirectStateAccess.glNamedBufferSubData(buffer, offset, data);
      }

      @Override
      void bufferStorage(final int buffer, final long size, @GpuBuffer.Usage final int usage) {
         ARBDirectStateAccess.glNamedBufferStorage(buffer, size, GlConst.bufferUsageToGlFlag(usage));
      }

      @Override
      void bufferStorage(final int buffer, final ByteBuffer data, @GpuBuffer.Usage final int usage) {
         ARBDirectStateAccess.glNamedBufferStorage(buffer, data, GlConst.bufferUsageToGlFlag(usage));
      }

      @Override
      @Nullable ByteBuffer mapBufferRange(final int buffer, final long offset, final long length, final int flags, @GpuBuffer.Usage final int usage) {
         return ARBDirectStateAccess.glMapNamedBufferRange(buffer, offset, length, flags);
      }

      @Override
      void unmapBuffer(final int buffer, final int usage) {
         ARBDirectStateAccess.glUnmapNamedBuffer(buffer);
      }

      @Override
      public int createFrameBufferObject() {
         return ARBDirectStateAccess.glCreateFramebuffers();
      }

      @Override
      public void bindFrameBufferTextures(final int fbo, final int color0, final int depth, final int mipLevel, @GpuBuffer.Usage final int bindSlot) {
         ARBDirectStateAccess.glNamedFramebufferTexture(fbo, 36064, color0, mipLevel);
         ARBDirectStateAccess.glNamedFramebufferTexture(fbo, 36096, depth, mipLevel);
         if (bindSlot != 0) {
            GlStateManager._glBindFramebuffer(bindSlot, fbo);
         }
      }

      @Override
      public void blitFrameBuffers(
         final int source,
         final int dest,
         final int srcX0,
         final int srcY0,
         final int srcX1,
         final int srcY1,
         final int dstX0,
         final int dstY0,
         final int dstX1,
         final int dstY1,
         final int mask,
         final int filter
      ) {
         ARBDirectStateAccess.glBlitNamedFramebuffer(source, dest, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
      }

      @Override
      void flushMappedBufferRange(final int handle, final long offset, final long length, @GpuBuffer.Usage final int usage) {
         ARBDirectStateAccess.glFlushMappedNamedBufferRange(handle, offset, length);
      }

      @Override
      void copyBufferSubData(final int source, final int target, final long sourceOffset, final long targetOffset, final long length) {
         ARBDirectStateAccess.glCopyNamedBufferSubData(source, target, sourceOffset, targetOffset, length);
      }
   }

   private static class Emulated extends DirectStateAccess {
      private int selectBufferBindTarget(@GpuBuffer.Usage final int usage) {
         if ((usage & 32) != 0) {
            return 34962;
         } else if ((usage & 64) != 0) {
            return 34963;
         } else {
            return (usage & 128) != 0 ? 35345 : 36663;
         }
      }

      @Override
      int createBuffer() {
         return GlStateManager._glGenBuffers();
      }

      @Override
      void bufferData(final int buffer, final long size, @GpuBuffer.Usage final int usage) {
         int target = this.selectBufferBindTarget(usage);
         GlStateManager._glBindBuffer(target, buffer);
         GlStateManager._glBufferData(target, size, GlConst.bufferUsageToGlEnum(usage));
         GlStateManager._glBindBuffer(target, 0);
      }

      @Override
      void bufferData(final int buffer, final ByteBuffer data, @GpuBuffer.Usage final int usage) {
         int target = this.selectBufferBindTarget(usage);
         GlStateManager._glBindBuffer(target, buffer);
         GlStateManager._glBufferData(target, data, GlConst.bufferUsageToGlEnum(usage));
         GlStateManager._glBindBuffer(target, 0);
      }

      @Override
      void bufferSubData(final int buffer, final long offset, final ByteBuffer data, @GpuBuffer.Usage final int usage) {
         int target = this.selectBufferBindTarget(usage);
         GlStateManager._glBindBuffer(target, buffer);
         GlStateManager._glBufferSubData(target, offset, data);
         GlStateManager._glBindBuffer(target, 0);
      }

      @Override
      void bufferStorage(final int buffer, final long size, @GpuBuffer.Usage final int usage) {
         int target = this.selectBufferBindTarget(usage);
         GlStateManager._glBindBuffer(target, buffer);
         ARBBufferStorage.glBufferStorage(target, size, GlConst.bufferUsageToGlFlag(usage));
         GlStateManager._glBindBuffer(target, 0);
      }

      @Override
      void bufferStorage(final int buffer, final ByteBuffer data, @GpuBuffer.Usage final int usage) {
         int target = this.selectBufferBindTarget(usage);
         GlStateManager._glBindBuffer(target, buffer);
         ARBBufferStorage.glBufferStorage(target, data, GlConst.bufferUsageToGlFlag(usage));
         GlStateManager._glBindBuffer(target, 0);
      }

      @Override
      @Nullable ByteBuffer mapBufferRange(final int buffer, final long offset, final long length, final int access, @GpuBuffer.Usage final int usage) {
         int target = this.selectBufferBindTarget(usage);
         GlStateManager._glBindBuffer(target, buffer);
         ByteBuffer byteBuffer = GlStateManager._glMapBufferRange(target, offset, length, access);
         GlStateManager._glBindBuffer(target, 0);
         return byteBuffer;
      }

      @Override
      void unmapBuffer(final int buffer, @GpuBuffer.Usage final int usage) {
         int target = this.selectBufferBindTarget(usage);
         GlStateManager._glBindBuffer(target, buffer);
         GlStateManager._glUnmapBuffer(target);
         GlStateManager._glBindBuffer(target, 0);
      }

      @Override
      void flushMappedBufferRange(final int buffer, final long offset, final long length, @GpuBuffer.Usage final int usage) {
         int target = this.selectBufferBindTarget(usage);
         GlStateManager._glBindBuffer(target, buffer);
         GL30.glFlushMappedBufferRange(target, offset, length);
         GlStateManager._glBindBuffer(target, 0);
      }

      @Override
      void copyBufferSubData(final int source, final int target, final long sourceOffset, final long targetOffset, final long length) {
         GlStateManager._glBindBuffer(36662, source);
         GlStateManager._glBindBuffer(36663, target);
         GL31.glCopyBufferSubData(36662, 36663, sourceOffset, targetOffset, length);
         GlStateManager._glBindBuffer(36662, 0);
         GlStateManager._glBindBuffer(36663, 0);
      }

      @Override
      public int createFrameBufferObject() {
         return GlStateManager.glGenFramebuffers();
      }

      @Override
      public void bindFrameBufferTextures(final int fbo, final int color0, final int depth, final int mipLevel, final int bindSlot) {
         int tempBindSlot = bindSlot == 0 ? 36009 : bindSlot;
         int oldFbo = GlStateManager.getFrameBuffer(tempBindSlot);
         GlStateManager._glBindFramebuffer(tempBindSlot, fbo);
         GlStateManager._glFramebufferTexture2D(tempBindSlot, 36064, 3553, color0, mipLevel);
         GlStateManager._glFramebufferTexture2D(tempBindSlot, 36096, 3553, depth, mipLevel);
         if (bindSlot == 0) {
            GlStateManager._glBindFramebuffer(tempBindSlot, oldFbo);
         }
      }

      @Override
      public void blitFrameBuffers(
         final int source,
         final int dest,
         final int srcX0,
         final int srcY0,
         final int srcX1,
         final int srcY1,
         final int dstX0,
         final int dstY0,
         final int dstX1,
         final int dstY1,
         final int mask,
         final int filter
      ) {
         int oldRead = GlStateManager.getFrameBuffer(36008);
         int oldDraw = GlStateManager.getFrameBuffer(36009);
         GlStateManager._glBindFramebuffer(36008, source);
         GlStateManager._glBindFramebuffer(36009, dest);
         GlStateManager._glBlitFrameBuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
         GlStateManager._glBindFramebuffer(36008, oldRead);
         GlStateManager._glBindFramebuffer(36009, oldDraw);
      }
   }
}
