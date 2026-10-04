package com.mojang.blaze3d.buffers;

public interface GpuFence extends AutoCloseable {
   @Override
   void close();

   boolean awaitCompletion(final long timeoutMs);
}
