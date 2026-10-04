package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class SectionOcclusionGraph {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Direction[] DIRECTIONS = Direction.values();
   private static final int MINIMUM_ADVANCED_CULLING_DISTANCE = 60;
   private static final int MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE = SectionPos.blockToSectionCoord(60);
   private static final double CEILED_SECTION_DIAGONAL = Math.ceil(Math.sqrt(3.0) * 16.0);
   private boolean needsFullUpdate = true;
   private @Nullable Future<?> fullUpdateTask;
   private @Nullable ViewArea viewArea;
   private final AtomicReference<SectionOcclusionGraph.@Nullable GraphState> currentGraph = new AtomicReference<>();
   private final AtomicReference<SectionOcclusionGraph.@Nullable GraphEvents> nextGraphEvents = new AtomicReference<>();
   private final AtomicBoolean needsFrustumUpdate = new AtomicBoolean(false);

   public void waitAndReset(final @Nullable ViewArea viewArea) {
      if (this.fullUpdateTask != null) {
         try {
            this.fullUpdateTask.get();
            this.fullUpdateTask = null;
         } catch (Exception e) {
            LOGGER.warn("Full update failed", e);
         }
      }

      this.viewArea = viewArea;
      if (viewArea != null) {
         this.currentGraph.set(new SectionOcclusionGraph.GraphState(viewArea));
         this.invalidate();
      } else {
         this.currentGraph.set(null);
      }
   }

   public void invalidate() {
      this.needsFullUpdate = true;
   }

   public void addSectionsInFrustum(
      final Frustum frustum,
      final List<SectionRenderDispatcher.RenderSection> visibleSections,
      final List<SectionRenderDispatcher.RenderSection> nearbyVisibleSection
   ) {
      this.currentGraph.get().storage().sectionTree.visitNodes((node, fullyVisible, depth, isClose) -> {
         SectionRenderDispatcher.RenderSection renderSection = node.getSection();
         if (renderSection != null) {
            visibleSections.add(renderSection);
            if (isClose) {
               nearbyVisibleSection.add(renderSection);
            }
         }
      }, frustum, 32);
   }

   public boolean consumeFrustumUpdate() {
      return this.needsFrustumUpdate.compareAndSet(true, false);
   }

   public void onChunkReadyToRender(final ChunkPos pos) {
      SectionOcclusionGraph.GraphEvents nextEvents = this.nextGraphEvents.get();
      if (nextEvents != null) {
         this.addNeighbors(nextEvents, pos);
      }

      SectionOcclusionGraph.GraphEvents events = this.currentGraph.get().events;
      if (events != nextEvents) {
         this.addNeighbors(events, pos);
      }
   }

   public void schedulePropagationFrom(final SectionRenderDispatcher.RenderSection section) {
      SectionOcclusionGraph.GraphEvents nextEvents = this.nextGraphEvents.get();
      if (nextEvents != null) {
         nextEvents.sectionsToPropagateFrom.add(section);
      }

      SectionOcclusionGraph.GraphEvents events = this.currentGraph.get().events;
      if (events != nextEvents) {
         events.sectionsToPropagateFrom.add(section);
      }
   }

   public void update(
      final boolean smartCull,
      final Camera camera,
      final Frustum frustum,
      final List<SectionRenderDispatcher.RenderSection> visibleSections,
      final LongOpenHashSet loadedEmptySections
   ) {
      Vec3 cameraPos = camera.position();
      if (this.needsFullUpdate && (this.fullUpdateTask == null || this.fullUpdateTask.isDone())) {
         this.scheduleFullUpdate(smartCull, camera, cameraPos, loadedEmptySections);
      }

      this.runPartialUpdate(smartCull, frustum, visibleSections, cameraPos, loadedEmptySections);
   }

   private void scheduleFullUpdate(final boolean smartCull, final Camera camera, final Vec3 cameraPos, final LongOpenHashSet loadedEmptySections) {
      this.needsFullUpdate = false;
      LongOpenHashSet emptySections = loadedEmptySections.clone();
      this.fullUpdateTask = CompletableFuture.runAsync(() -> {
         SectionOcclusionGraph.GraphState newState = new SectionOcclusionGraph.GraphState(this.viewArea);
         this.nextGraphEvents.set(newState.events);
         Queue<SectionOcclusionGraph.Node> queue = Queues.newArrayDeque();
         this.initializeQueueForFullUpdate(camera, queue);
         queue.forEach(node -> newState.storage.sectionToNodeMap.put(node.section, node));
         this.runUpdates(newState.storage, cameraPos, queue, smartCull, node -> {}, emptySections);
         this.currentGraph.set(newState);
         this.nextGraphEvents.set(null);
         this.needsFrustumUpdate.set(true);
      }, Util.backgroundExecutor());
   }

   private void runPartialUpdate(
      final boolean smartCull,
      final Frustum frustum,
      final List<SectionRenderDispatcher.RenderSection> visibleSections,
      final Vec3 cameraPos,
      final LongOpenHashSet loadedEmptySections
   ) {
      SectionOcclusionGraph.GraphState state = this.currentGraph.get();
      this.queueSectionsWithNewNeighbors(state);
      if (!state.events.sectionsToPropagateFrom.isEmpty()) {
         Queue<SectionOcclusionGraph.Node> queue = Queues.newArrayDeque();

         while (!state.events.sectionsToPropagateFrom.isEmpty()) {
            SectionRenderDispatcher.RenderSection renderSection = state.events.sectionsToPropagateFrom.poll();
            SectionOcclusionGraph.Node node = state.storage.sectionToNodeMap.get(renderSection);
            if (node != null && node.section == renderSection) {
               queue.add(node);
            }
         }

         Frustum offsetFrustum = LevelRenderer.offsetFrustum(frustum);
         Consumer<SectionRenderDispatcher.RenderSection> onSectionAdded = section -> {
            if (offsetFrustum.isVisible(section.getBoundingBox())) {
               this.needsFrustumUpdate.set(true);
            }
         };
         this.runUpdates(state.storage, cameraPos, queue, smartCull, onSectionAdded, loadedEmptySections);
      }
   }

   private void queueSectionsWithNewNeighbors(final SectionOcclusionGraph.GraphState state) {
      LongIterator iterator = state.events.chunksWhichReceivedNeighbors.iterator();

      while (iterator.hasNext()) {
         long chunkWithNewNeighbor = iterator.nextLong();
         List<SectionRenderDispatcher.RenderSection> renderSections = (List<SectionRenderDispatcher.RenderSection>)state.storage
            .chunksWaitingForNeighbors
            .get(chunkWithNewNeighbor);
         if (renderSections != null && renderSections.get(0).hasAllNeighbors()) {
            state.events.sectionsToPropagateFrom.addAll(renderSections);
            state.storage.chunksWaitingForNeighbors.remove(chunkWithNewNeighbor);
         }
      }

      state.events.chunksWhichReceivedNeighbors.clear();
   }

   private void addNeighbors(final SectionOcclusionGraph.GraphEvents events, final ChunkPos pos) {
      events.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(pos.x - 1, pos.z));
      events.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(pos.x, pos.z - 1));
      events.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(pos.x + 1, pos.z));
      events.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(pos.x, pos.z + 1));
      events.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(pos.x - 1, pos.z - 1));
      events.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(pos.x - 1, pos.z + 1));
      events.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(pos.x + 1, pos.z - 1));
      events.chunksWhichReceivedNeighbors.add(ChunkPos.asLong(pos.x + 1, pos.z + 1));
   }

   private void initializeQueueForFullUpdate(final Camera camera, final Queue<SectionOcclusionGraph.Node> queue) {
      BlockPos cameraPosition = camera.blockPosition();
      long cameraSectionNode = SectionPos.asLong(cameraPosition);
      int cameraSectionY = SectionPos.y(cameraSectionNode);
      SectionRenderDispatcher.RenderSection cameraSection = this.viewArea.getRenderSection(cameraSectionNode);
      if (cameraSection == null) {
         LevelHeightAccessor heightAccessor = this.viewArea.getLevelHeightAccessor();
         boolean isBelowTheWorld = cameraSectionY < heightAccessor.getMinSectionY();
         int sectionY = isBelowTheWorld ? heightAccessor.getMinSectionY() : heightAccessor.getMaxSectionY();
         int viewDistance = this.viewArea.getViewDistance();
         List<SectionOcclusionGraph.Node> toAdd = Lists.newArrayList();
         int cameraSectionX = SectionPos.x(cameraSectionNode);
         int cameraSectionZ = SectionPos.z(cameraSectionNode);

         for (int sectionX = -viewDistance; sectionX <= viewDistance; sectionX++) {
            for (int sectionZ = -viewDistance; sectionZ <= viewDistance; sectionZ++) {
               SectionRenderDispatcher.RenderSection renderSectionAt = this.viewArea
                  .getRenderSection(SectionPos.asLong(sectionX + cameraSectionX, sectionY, sectionZ + cameraSectionZ));
               if (renderSectionAt != null && this.isInViewDistance(cameraSectionNode, renderSectionAt.getSectionNode())) {
                  Direction sourceDirection = isBelowTheWorld ? Direction.UP : Direction.DOWN;
                  SectionOcclusionGraph.Node node = new SectionOcclusionGraph.Node(renderSectionAt, sourceDirection, 0);
                  node.setDirections(node.directions, sourceDirection);
                  if (sectionX > 0) {
                     node.setDirections(node.directions, Direction.EAST);
                  } else if (sectionX < 0) {
                     node.setDirections(node.directions, Direction.WEST);
                  }

                  if (sectionZ > 0) {
                     node.setDirections(node.directions, Direction.SOUTH);
                  } else if (sectionZ < 0) {
                     node.setDirections(node.directions, Direction.NORTH);
                  }

                  toAdd.add(node);
               }
            }
         }

         toAdd.sort(Comparator.comparingDouble(c -> cameraPosition.distSqr(SectionPos.of(c.section.getSectionNode()).center())));
         queue.addAll(toAdd);
      } else {
         queue.add(new SectionOcclusionGraph.Node(cameraSection, null, 0));
      }
   }

   private void runUpdates(
      final SectionOcclusionGraph.GraphStorage storage,
      final Vec3 cameraPos,
      final Queue<SectionOcclusionGraph.Node> queue,
      final boolean smartCull,
      final Consumer<SectionRenderDispatcher.RenderSection> onSectionAdded,
      final LongOpenHashSet emptySections
   ) {
      SectionPos cameraSectionPos = SectionPos.of(cameraPos);
      long cameraSectionNode = cameraSectionPos.asLong();
      BlockPos cameraSectionCenter = cameraSectionPos.center();

      while (!queue.isEmpty()) {
         SectionOcclusionGraph.Node node = queue.poll();
         SectionRenderDispatcher.RenderSection currentSection = node.section;
         if (!emptySections.contains(node.section.getSectionNode())) {
            if (storage.sectionTree.add(node.section)) {
               onSectionAdded.accept(node.section);
            }
         } else {
            node.section.sectionMesh.compareAndSet(CompiledSectionMesh.UNCOMPILED, CompiledSectionMesh.EMPTY);
         }

         long sectionNode = currentSection.getSectionNode();
         boolean distantFromCamera = Math.abs(SectionPos.x(sectionNode) - cameraSectionPos.x()) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE
            || Math.abs(SectionPos.y(sectionNode) - cameraSectionPos.y()) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE
            || Math.abs(SectionPos.z(sectionNode) - cameraSectionPos.z()) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE;

         for (Direction direction : DIRECTIONS) {
            SectionRenderDispatcher.RenderSection renderSectionAt = this.getRelativeFrom(cameraSectionNode, currentSection, direction);
            if (renderSectionAt != null && (!smartCull || !node.hasDirection(direction.getOpposite()))) {
               if (smartCull && node.hasSourceDirections()) {
                  SectionMesh sectionMesh = currentSection.getSectionMesh();
                  boolean visible = false;

                  for (int i = 0; i < DIRECTIONS.length; i++) {
                     if (node.hasSourceDirection(i) && sectionMesh.facesCanSeeEachother(DIRECTIONS[i].getOpposite(), direction)) {
                        visible = true;
                        break;
                     }
                  }

                  if (!visible) {
                     continue;
                  }
               }

               if (smartCull && distantFromCamera) {
                  int renderSectionOriginX = SectionPos.sectionToBlockCoord(SectionPos.x(sectionNode));
                  int renderSectionOriginY = SectionPos.sectionToBlockCoord(SectionPos.y(sectionNode));
                  int renderSectionOriginZ = SectionPos.sectionToBlockCoord(SectionPos.z(sectionNode));
                  boolean maxX = direction.getAxis() == Direction.Axis.X
                     ? cameraSectionCenter.getX() > renderSectionOriginX
                     : cameraSectionCenter.getX() < renderSectionOriginX;
                  boolean maxY = direction.getAxis() == Direction.Axis.Y
                     ? cameraSectionCenter.getY() > renderSectionOriginY
                     : cameraSectionCenter.getY() < renderSectionOriginY;
                  boolean maxZ = direction.getAxis() == Direction.Axis.Z
                     ? cameraSectionCenter.getZ() > renderSectionOriginZ
                     : cameraSectionCenter.getZ() < renderSectionOriginZ;
                  Vector3d checkPos = new Vector3d(
                     renderSectionOriginX + (maxX ? 16 : 0), renderSectionOriginY + (maxY ? 16 : 0), renderSectionOriginZ + (maxZ ? 16 : 0)
                  );
                  Vector3d step = new Vector3d(cameraPos.x, cameraPos.y, cameraPos.z).sub(checkPos).normalize().mul(CEILED_SECTION_DIAGONAL);
                  boolean visible = true;

                  while (checkPos.distanceSquared(cameraPos.x, cameraPos.y, cameraPos.z) > 3600.0) {
                     checkPos.add(step);
                     LevelHeightAccessor heightAccessor = this.viewArea.getLevelHeightAccessor();
                     if (checkPos.y > heightAccessor.getMaxY() || checkPos.y < heightAccessor.getMinY()) {
                        break;
                     }

                     SectionRenderDispatcher.RenderSection checkSection = this.viewArea
                        .getRenderSectionAt(BlockPos.containing(checkPos.x, checkPos.y, checkPos.z));
                     if (checkSection == null || storage.sectionToNodeMap.get(checkSection) == null) {
                        visible = false;
                        break;
                     }
                  }

                  if (!visible) {
                     continue;
                  }
               }

               SectionOcclusionGraph.Node existingNode = storage.sectionToNodeMap.get(renderSectionAt);
               if (existingNode != null) {
                  existingNode.addSourceDirection(direction);
               } else {
                  SectionOcclusionGraph.Node newNode = new SectionOcclusionGraph.Node(renderSectionAt, direction, node.step + 1);
                  newNode.setDirections(node.directions, direction);
                  if (renderSectionAt.hasAllNeighbors()) {
                     queue.add(newNode);
                     storage.sectionToNodeMap.put(renderSectionAt, newNode);
                  } else if (this.isInViewDistance(cameraSectionNode, renderSectionAt.getSectionNode())) {
                     storage.sectionToNodeMap.put(renderSectionAt, newNode);
                     long chunkNode = SectionPos.sectionToChunk(renderSectionAt.getSectionNode());
                     ((List)storage.chunksWaitingForNeighbors.computeIfAbsent(chunkNode, l -> new ArrayList())).add(renderSectionAt);
                  }
               }
            }
         }
      }
   }

   private boolean isInViewDistance(final long cameraSectionNode, final long sectionNode) {
      return ChunkTrackingView.isInViewDistance(
         SectionPos.x(cameraSectionNode),
         SectionPos.z(cameraSectionNode),
         this.viewArea.getViewDistance(),
         SectionPos.x(sectionNode),
         SectionPos.z(sectionNode)
      );
   }

   private SectionRenderDispatcher.@Nullable RenderSection getRelativeFrom(
      final long cameraSectionNode, final SectionRenderDispatcher.RenderSection renderSection, final Direction direction
   ) {
      long relative = renderSection.getNeighborSectionNode(direction);
      if (!this.isInViewDistance(cameraSectionNode, relative)) {
         return null;
      } else {
         return Mth.abs(SectionPos.y(cameraSectionNode) - SectionPos.y(relative)) > this.viewArea.getViewDistance()
            ? null
            : this.viewArea.getRenderSection(relative);
      }
   }

   @VisibleForDebug
   public SectionOcclusionGraph.@Nullable Node getNode(final SectionRenderDispatcher.RenderSection section) {
      return this.currentGraph.get().storage.sectionToNodeMap.get(section);
   }

   public Octree getOctree() {
      return this.currentGraph.get().storage.sectionTree;
   }

   private record GraphEvents(LongSet chunksWhichReceivedNeighbors, BlockingQueue<SectionRenderDispatcher.RenderSection> sectionsToPropagateFrom) {
      private GraphEvents() {
         this(new LongOpenHashSet(), new LinkedBlockingQueue<>());
      }
   }

   private record GraphState(SectionOcclusionGraph.GraphStorage storage, SectionOcclusionGraph.GraphEvents events) {
      private GraphState(final ViewArea viewArea) {
         this(new SectionOcclusionGraph.GraphStorage(viewArea), new SectionOcclusionGraph.GraphEvents());
      }
   }

   private static class GraphStorage {
      public final SectionOcclusionGraph.SectionToNodeMap sectionToNodeMap;
      public final Octree sectionTree;
      public final Long2ObjectMap<List<SectionRenderDispatcher.RenderSection>> chunksWaitingForNeighbors;

      public GraphStorage(final ViewArea viewArea) {
         this.sectionToNodeMap = new SectionOcclusionGraph.SectionToNodeMap(viewArea.sections.length);
         this.sectionTree = new Octree(viewArea.getCameraSectionPos(), viewArea.getViewDistance(), viewArea.sectionGridSizeY, viewArea.level.getMinY());
         this.chunksWaitingForNeighbors = new Long2ObjectOpenHashMap();
      }
   }

   @VisibleForDebug
   public static class Node {
      @VisibleForDebug
      protected final SectionRenderDispatcher.RenderSection section;
      private byte sourceDirections;
      private byte directions;
      @VisibleForDebug
      public final int step;

      private Node(final SectionRenderDispatcher.RenderSection section, final @Nullable Direction sourceDirection, final int step) {
         this.section = section;
         if (sourceDirection != null) {
            this.addSourceDirection(sourceDirection);
         }

         this.step = step;
      }

      private void setDirections(final byte oldDirections, final Direction direction) {
         this.directions = (byte)(this.directions | oldDirections | 1 << direction.ordinal());
      }

      private boolean hasDirection(final Direction direction) {
         return (this.directions & 1 << direction.ordinal()) > 0;
      }

      private void addSourceDirection(final Direction direction) {
         this.sourceDirections = (byte)(this.sourceDirections | this.sourceDirections | 1 << direction.ordinal());
      }

      @VisibleForDebug
      public boolean hasSourceDirection(final int directionOrdinal) {
         return (this.sourceDirections & 1 << directionOrdinal) > 0;
      }

      private boolean hasSourceDirections() {
         return this.sourceDirections != 0;
      }

      @Override
      public int hashCode() {
         return Long.hashCode(this.section.getSectionNode());
      }

      @Override
      public boolean equals(final Object obj) {
         return !(obj instanceof SectionOcclusionGraph.Node other) ? false : this.section.getSectionNode() == other.section.getSectionNode();
      }
   }

   private static class SectionToNodeMap {
      private final SectionOcclusionGraph.Node[] nodes;

      private SectionToNodeMap(final int sectionCount) {
         this.nodes = new SectionOcclusionGraph.Node[sectionCount];
      }

      public void put(final SectionRenderDispatcher.RenderSection renderSection, final SectionOcclusionGraph.Node node) {
         this.nodes[renderSection.index] = node;
      }

      public SectionOcclusionGraph.@Nullable Node get(final SectionRenderDispatcher.RenderSection renderSection) {
         int index = renderSection.index;
         return index >= 0 && index < this.nodes.length ? this.nodes[index] : null;
      }
   }
}
