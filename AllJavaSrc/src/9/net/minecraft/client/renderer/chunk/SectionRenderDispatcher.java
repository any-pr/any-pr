package net.minecraft.client.renderer.chunk;

import com.google.common.collect.Queues;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.ByteBufferBuilder.Result;
import com.mojang.blaze3d.vertex.MeshData.SortState;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.CrashReport;
import net.minecraft.TracingExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.SectionBufferBuilderPool;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SectionRenderDispatcher {
   private final CompileTaskDynamicQueue compileQueue = new CompileTaskDynamicQueue();
   private final Queue<Runnable> toUpload = Queues.newConcurrentLinkedQueue();
   private final Executor mainThreadUploadExecutor = this.toUpload::add;
   private final Queue<SectionMesh> toClose = Queues.newConcurrentLinkedQueue();
   private final SectionBufferBuilderPack fixedBuffers;
   private final SectionBufferBuilderPool bufferPool;
   private volatile boolean closed;
   private final ConsecutiveExecutor consecutiveExecutor;
   private final TracingExecutor executor;
   private ClientLevel level;
   private final LevelRenderer renderer;
   private Vec3 cameraPosition = Vec3.ZERO;
   private final SectionCompiler sectionCompiler;

   public SectionRenderDispatcher(
      final ClientLevel level,
      final LevelRenderer renderer,
      final TracingExecutor executor,
      final RenderBuffers renderBuffers,
      final BlockRenderDispatcher blockRenderer,
      final BlockEntityRenderDispatcher blockEntityRenderDispatcher
   ) {
      this.level = level;
      this.renderer = renderer;
      this.fixedBuffers = renderBuffers.fixedBufferPack();
      this.bufferPool = renderBuffers.sectionBufferPool();
      this.executor = executor;
      this.consecutiveExecutor = new ConsecutiveExecutor(executor, "Section Renderer");
      this.consecutiveExecutor.schedule(this::runTask);
      this.sectionCompiler = new SectionCompiler(blockRenderer, blockEntityRenderDispatcher);
   }

   public void setLevel(final ClientLevel level) {
      this.level = level;
   }

   private void runTask() {
      if (!this.closed && !this.bufferPool.isEmpty()) {
         SectionRenderDispatcher.RenderSection.CompileTask task = this.compileQueue.poll(this.cameraPosition);
         if (task != null) {
            SectionBufferBuilderPack buffer = Objects.requireNonNull(this.bufferPool.acquire());
            CompletableFuture.<CompletableFuture<SectionRenderDispatcher.SectionTaskResult>>supplyAsync(
                  () -> task.doTask(buffer), this.executor.forName(task.name())
               )
               .thenCompose(f -> (CompletionStage<SectionRenderDispatcher.SectionTaskResult>)f)
               .whenComplete((result, throwable) -> {
                  if (throwable != null) {
                     Minecraft.getInstance().delayCrash(CrashReport.forThrowable(throwable, "Batching sections"));
                  } else {
                     task.isCompleted.set(true);
                     this.consecutiveExecutor.schedule(() -> {
                        if (result == SectionRenderDispatcher.SectionTaskResult.SUCCESSFUL) {
                           buffer.clearAll();
                        } else {
                           buffer.discardAll();
                        }

                        this.bufferPool.release(buffer);
                        this.runTask();
                     });
                  }
               });
         }
      }
   }

   public void setCameraPosition(final Vec3 cameraPosition) {
      this.cameraPosition = cameraPosition;
   }

   public void uploadAllPendingUploads() {
      Runnable upload;
      while ((upload = this.toUpload.poll()) != null) {
         upload.run();
      }

      SectionMesh mesh;
      while ((mesh = this.toClose.poll()) != null) {
         mesh.close();
      }
   }

   public void rebuildSectionSync(final SectionRenderDispatcher.RenderSection section, final RenderRegionCache cache) {
      section.compileSync(cache);
   }

   public void schedule(final SectionRenderDispatcher.RenderSection.CompileTask task) {
      if (!this.closed) {
         this.consecutiveExecutor.schedule(() -> {
            if (!this.closed) {
               this.compileQueue.add(task);
               this.runTask();
            }
         });
      }
   }

   public void clearCompileQueue() {
      this.compileQueue.clear();
   }

   public boolean isQueueEmpty() {
      return this.compileQueue.size() == 0 && this.toUpload.isEmpty();
   }

   public void dispose() {
      this.closed = true;
      this.clearCompileQueue();
      this.uploadAllPendingUploads();
   }

   @VisibleForDebug
   public String getStats() {
      return String.format(Locale.ROOT, "pC: %03d, pU: %02d, aB: %02d", this.compileQueue.size(), this.toUpload.size(), this.bufferPool.getFreeBufferCount());
   }

   @VisibleForDebug
   public int getCompileQueueSize() {
      return this.compileQueue.size();
   }

   @VisibleForDebug
   public int getToUpload() {
      return this.toUpload.size();
   }

   @VisibleForDebug
   public int getFreeBufferCount() {
      return this.bufferPool.getFreeBufferCount();
   }

   public class RenderSection {
      public static final int SIZE = 16;
      public final int index;
      public final AtomicReference<SectionMesh> sectionMesh = new AtomicReference<>(CompiledSectionMesh.UNCOMPILED);
      private SectionRenderDispatcher.RenderSection.@Nullable RebuildTask lastRebuildTask;
      private SectionRenderDispatcher.RenderSection.@Nullable ResortTransparencyTask lastResortTransparencyTask;
      private AABB bb;
      private boolean dirty = true;
      private volatile long sectionNode = SectionPos.asLong(-1, -1, -1);
      private final BlockPos.MutableBlockPos renderOrigin = new BlockPos.MutableBlockPos(-1, -1, -1);
      private boolean playerChanged;
      private long uploadedTime;
      private long fadeDuration;
      private boolean wasPreviouslyEmpty;

      public RenderSection(final int index, final long sectionNode) {
         this.index = index;
         this.setSectionNode(sectionNode);
      }

      public float getVisibility(final long now) {
         long elapsed = now - this.uploadedTime;
         return elapsed >= this.fadeDuration ? 1.0F : (float)elapsed / (float)this.fadeDuration;
      }

      public void setFadeDuration(final long fadeDuration) {
         this.fadeDuration = fadeDuration;
      }

      public void setWasPreviouslyEmpty(final boolean wasPreviouslyEmpty) {
         this.wasPreviouslyEmpty = wasPreviouslyEmpty;
      }

      public boolean wasPreviouslyEmpty() {
         return this.wasPreviouslyEmpty;
      }

      private boolean doesChunkExistAt(final long sectionNode) {
         ChunkAccess chunk = SectionRenderDispatcher.this.level.getChunk(SectionPos.x(sectionNode), SectionPos.z(sectionNode), ChunkStatus.FULL, false);
         return chunk != null && SectionRenderDispatcher.this.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(sectionNode));
      }

      public boolean hasAllNeighbors() {
         return this.doesChunkExistAt(SectionPos.offset(this.sectionNode, Direction.WEST))
            && this.doesChunkExistAt(SectionPos.offset(this.sectionNode, Direction.NORTH))
            && this.doesChunkExistAt(SectionPos.offset(this.sectionNode, Direction.EAST))
            && this.doesChunkExistAt(SectionPos.offset(this.sectionNode, Direction.SOUTH))
            && this.doesChunkExistAt(SectionPos.offset(this.sectionNode, -1, 0, -1))
            && this.doesChunkExistAt(SectionPos.offset(this.sectionNode, -1, 0, 1))
            && this.doesChunkExistAt(SectionPos.offset(this.sectionNode, 1, 0, -1))
            && this.doesChunkExistAt(SectionPos.offset(this.sectionNode, 1, 0, 1));
      }

      public AABB getBoundingBox() {
         return this.bb;
      }

      public CompletableFuture<Void> upload(final Map<ChunkSectionLayer, MeshData> renderedLayers, final CompiledSectionMesh compiledSectionMesh) {
         if (SectionRenderDispatcher.this.closed) {
            renderedLayers.values().forEach(MeshData::close);
            return CompletableFuture.completedFuture(null);
         } else {
            return CompletableFuture.runAsync(() -> renderedLayers.forEach((layer, mesh) -> {
               try (Zone ignored = Profiler.get().zone("Upload Section Layer")) {
                  compiledSectionMesh.uploadMeshLayer(layer, mesh, this.sectionNode);
                  mesh.close();
               }

               if (this.uploadedTime == 0L) {
                  this.uploadedTime = Util.getMillis();
               }
            }), SectionRenderDispatcher.this.mainThreadUploadExecutor);
         }
      }

      public CompletableFuture<Void> uploadSectionIndexBuffer(
         final CompiledSectionMesh compiledSectionMesh, final Result indexBuffer, final ChunkSectionLayer layer
      ) {
         if (SectionRenderDispatcher.this.closed) {
            indexBuffer.close();
            return CompletableFuture.completedFuture(null);
         } else {
            return CompletableFuture.runAsync(() -> {
               try (Zone ignored = Profiler.get().zone("Upload Section Indices")) {
                  compiledSectionMesh.uploadLayerIndexBuffer(layer, indexBuffer, this.sectionNode);
                  indexBuffer.close();
               }
            }, SectionRenderDispatcher.this.mainThreadUploadExecutor);
         }
      }

      public void setSectionNode(final long sectionNode) {
         this.reset();
         this.sectionNode = sectionNode;
         int x = SectionPos.sectionToBlockCoord(SectionPos.x(sectionNode));
         int y = SectionPos.sectionToBlockCoord(SectionPos.y(sectionNode));
         int z = SectionPos.sectionToBlockCoord(SectionPos.z(sectionNode));
         this.renderOrigin.set(x, y, z);
         this.bb = new AABB(x, y, z, x + 16, y + 16, z + 16);
      }

      public SectionMesh getSectionMesh() {
         return this.sectionMesh.get();
      }

      public void reset() {
         this.cancelTasks();
         this.sectionMesh.getAndSet(CompiledSectionMesh.UNCOMPILED).close();
         this.dirty = true;
         this.uploadedTime = 0L;
         this.wasPreviouslyEmpty = false;
      }

      public BlockPos getRenderOrigin() {
         return this.renderOrigin;
      }

      public long getSectionNode() {
         return this.sectionNode;
      }

      public void setDirty(final boolean fromPlayer) {
         boolean wasDirty = this.dirty;
         this.dirty = true;
         this.playerChanged = fromPlayer | (wasDirty && this.playerChanged);
      }

      public void setNotDirty() {
         this.dirty = false;
         this.playerChanged = false;
      }

      public boolean isDirty() {
         return this.dirty;
      }

      public boolean isDirtyFromPlayer() {
         return this.dirty && this.playerChanged;
      }

      public long getNeighborSectionNode(final Direction direction) {
         return SectionPos.offset(this.sectionNode, direction);
      }

      public void resortTransparency(final SectionRenderDispatcher dispatcher) {
         if (this.getSectionMesh() instanceof CompiledSectionMesh mesh) {
            this.lastResortTransparencyTask = new SectionRenderDispatcher.RenderSection.ResortTransparencyTask(mesh);
            dispatcher.schedule(this.lastResortTransparencyTask);
         }
      }

      public boolean hasTranslucentGeometry() {
         return this.getSectionMesh().hasTranslucentGeometry();
      }

      public boolean transparencyResortingScheduled() {
         return this.lastResortTransparencyTask != null && !this.lastResortTransparencyTask.isCompleted.get();
      }

      protected void cancelTasks() {
         if (this.lastRebuildTask != null) {
            this.lastRebuildTask.cancel();
            this.lastRebuildTask = null;
         }

         if (this.lastResortTransparencyTask != null) {
            this.lastResortTransparencyTask.cancel();
            this.lastResortTransparencyTask = null;
         }
      }

      public SectionRenderDispatcher.RenderSection.CompileTask createCompileTask(final RenderRegionCache cache) {
         this.cancelTasks();
         RenderSectionRegion region = cache.createRegion(SectionRenderDispatcher.this.level, this.sectionNode);
         boolean isRecompile = this.sectionMesh.get() != CompiledSectionMesh.UNCOMPILED;
         this.lastRebuildTask = new SectionRenderDispatcher.RenderSection.RebuildTask(region, isRecompile);
         return this.lastRebuildTask;
      }

      public void rebuildSectionAsync(final RenderRegionCache cache) {
         SectionRenderDispatcher.RenderSection.CompileTask task = this.createCompileTask(cache);
         SectionRenderDispatcher.this.schedule(task);
      }

      public void compileSync(final RenderRegionCache cache) {
         SectionRenderDispatcher.RenderSection.CompileTask task = this.createCompileTask(cache);
         task.doTask(SectionRenderDispatcher.this.fixedBuffers);
      }

      private void setSectionMesh(final SectionMesh sectionMesh) {
         SectionMesh oldMesh = this.sectionMesh.getAndSet(sectionMesh);
         SectionRenderDispatcher.this.toClose.add(oldMesh);
         SectionRenderDispatcher.this.renderer.addRecentlyCompiledSection(this);
      }

      private VertexSorting createVertexSorting(final SectionPos sectionPos) {
         Vec3 camera = SectionRenderDispatcher.this.cameraPosition;
         return VertexSorting.byDistance(
            (float)(camera.x - sectionPos.minBlockX()), (float)(camera.y - sectionPos.minBlockY()), (float)(camera.z - sectionPos.minBlockZ())
         );
      }

      public abstract class CompileTask {
         protected final AtomicBoolean isCancelled = new AtomicBoolean(false);
         protected final AtomicBoolean isCompleted = new AtomicBoolean(false);
         protected final boolean isRecompile;

         public CompileTask(final boolean isRecompile) {
            this.isRecompile = isRecompile;
         }

         public abstract CompletableFuture<SectionRenderDispatcher.SectionTaskResult> doTask(final SectionBufferBuilderPack buffers);

         public abstract void cancel();

         protected abstract String name();

         public boolean isRecompile() {
            return this.isRecompile;
         }

         public BlockPos getRenderOrigin() {
            return RenderSection.this.renderOrigin;
         }
      }

      private class RebuildTask extends SectionRenderDispatcher.RenderSection.CompileTask {
         protected final RenderSectionRegion region;

         public RebuildTask(final RenderSectionRegion region, final boolean isRecompile) {
            super(isRecompile);
            this.region = region;
         }

         @Override
         protected String name() {
            return "rend_chk_rebuild";
         }

         @Override
         public CompletableFuture<SectionRenderDispatcher.SectionTaskResult> doTask(final SectionBufferBuilderPack buffers) {
            if (this.isCancelled.get()) {
               return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
            }

            long sectionNode = RenderSection.this.sectionNode;
            SectionPos sectionPos = SectionPos.of(sectionNode);
            if (this.isCancelled.get()) {
               return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
            }

            SectionCompiler.Results results;
            try (Zone ignored = Profiler.get().zone("Compile Section")) {
               results = SectionRenderDispatcher.this.sectionCompiler
                  .compile(sectionPos, this.region, RenderSection.this.createVertexSorting(sectionPos), buffers);
            }

            TranslucencyPointOfView translucencyPointOfView = TranslucencyPointOfView.of(SectionRenderDispatcher.this.cameraPosition, sectionNode);
            if (this.isCancelled.get()) {
               results.release();
               return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
            } else {
               CompiledSectionMesh compiledSectionMesh = new CompiledSectionMesh(translucencyPointOfView, results);
               CompletableFuture<Void> uploadFuture = RenderSection.this.upload(results.renderedLayers, compiledSectionMesh);
               return uploadFuture.handle((ignored, throwable) -> {
                  if (throwable != null && !(throwable instanceof CancellationException) && !(throwable instanceof InterruptedException)) {
                     Minecraft.getInstance().delayCrash(CrashReport.forThrowable(throwable, "Rendering section"));
                  }

                  if (!this.isCancelled.get() && !SectionRenderDispatcher.this.closed) {
                     RenderSection.this.setSectionMesh(compiledSectionMesh);
                     return SectionRenderDispatcher.SectionTaskResult.SUCCESSFUL;
                  } else {
                     SectionRenderDispatcher.this.toClose.add(compiledSectionMesh);
                     return SectionRenderDispatcher.SectionTaskResult.CANCELLED;
                  }
               });
            }
         }

         @Override
         public void cancel() {
            if (this.isCancelled.compareAndSet(false, true)) {
               RenderSection.this.setDirty(false);
            }
         }
      }

      private class ResortTransparencyTask extends SectionRenderDispatcher.RenderSection.CompileTask {
         private final CompiledSectionMesh compiledSectionMesh;

         public ResortTransparencyTask(final CompiledSectionMesh compiledSectionMesh) {
            super(true);
            this.compiledSectionMesh = compiledSectionMesh;
         }

         @Override
         protected String name() {
            return "rend_chk_sort";
         }

         @Override
         public CompletableFuture<SectionRenderDispatcher.SectionTaskResult> doTask(final SectionBufferBuilderPack buffers) {
            if (this.isCancelled.get()) {
               return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
            }

            SortState state = this.compiledSectionMesh.getTransparencyState();
            if (state != null && !this.compiledSectionMesh.isEmpty(ChunkSectionLayer.TRANSLUCENT)) {
               long sectionNode = RenderSection.this.sectionNode;
               VertexSorting vertexSorting = RenderSection.this.createVertexSorting(SectionPos.of(sectionNode));
               TranslucencyPointOfView translucencyPointOfView = TranslucencyPointOfView.of(SectionRenderDispatcher.this.cameraPosition, sectionNode);
               if (!this.compiledSectionMesh.isDifferentPointOfView(translucencyPointOfView) && !translucencyPointOfView.isAxisAligned()) {
                  return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
               } else {
                  Result indexBuffer = state.buildSortedIndexBuffer(buffers.buffer(ChunkSectionLayer.TRANSLUCENT), vertexSorting);
                  if (indexBuffer == null) {
                     return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
                  } else if (this.isCancelled.get()) {
                     indexBuffer.close();
                     return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
                  } else {
                     CompletableFuture<Void> future = RenderSection.this.uploadSectionIndexBuffer(
                        this.compiledSectionMesh, indexBuffer, ChunkSectionLayer.TRANSLUCENT
                     );
                     return future.handle((ignored, throwable) -> {
                        if (throwable != null && !(throwable instanceof CancellationException) && !(throwable instanceof InterruptedException)) {
                           Minecraft.getInstance().delayCrash(CrashReport.forThrowable(throwable, "Rendering section"));
                        }

                        if (this.isCancelled.get()) {
                           return SectionRenderDispatcher.SectionTaskResult.CANCELLED;
                        }

                        this.compiledSectionMesh.setTranslucencyPointOfView(translucencyPointOfView);
                        return SectionRenderDispatcher.SectionTaskResult.SUCCESSFUL;
                     });
                  }
               }
            } else {
               return CompletableFuture.completedFuture(SectionRenderDispatcher.SectionTaskResult.CANCELLED);
            }
         }

         @Override
         public void cancel() {
            this.isCancelled.set(true);
         }
      }
   }

   private enum SectionTaskResult {
      SUCCESSFUL,
      CANCELLED;
   }
}
