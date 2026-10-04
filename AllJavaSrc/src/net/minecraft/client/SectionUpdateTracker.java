package net.minecraft.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class SectionUpdateTracker {
    private final RotatingSectionStorage<SectionUpdateTracker.SectionDirtyState> storage;

    public SectionUpdateTracker(final LevelHeightAccessor levelHeightAccessor, final int renderDistance) {
        this(levelHeightAccessor.getMinSectionY(), levelHeightAccessor.getMaxSectionY(), renderDistance);
    }

    /**
     * 🔧 MCRe：显式 Y 范围构造器——让「渲染脏标记」范围与 {@code ViewArea} 的渲染窗口对齐。
     * <p>原版两者本就一致（都用 world 域的 min/maxSectionY）；超高世界里 {@code LevelHeightAccessor}
     * 被窗口钳制到 34 段（= {@code [minSectionY, minSectionY + 33]}），而 {@code ViewArea} 用
     * 「相机中心带 ± halfY」→ 窗口外的 section（如 Y=480 的 sectionY=30）永远拿不到 dirty 状态
     * → {@code LevelExtractor} 永不提取该 section → 方块不渲染 ✗✗
     * <p>因此本类必须由调用方传入与 {@code ViewArea} 完全相同的 Y 范围。
     */
    public SectionUpdateTracker(final int minSectionY, final int maxSectionY, final int renderDistance) {
        this.storage = new RotatingSectionStorage<>(
            renderDistance,
            minSectionY,
            maxSectionY,
            (index, sectionNode) -> new SectionUpdateTracker.SectionDirtyState(true, false, sectionNode)
        );
    }

    /** 🔧 MCRe：当前脏标记窗口底部 sectionY（应与 {@code ViewArea.minSectionY()} 一致） */
    public int minSectionY() {
        return this.storage.minY();
    }

    /** 🔧 MCRe：当前脏标记窗口顶部 sectionY（应与 {@code ViewArea.maxSectionY()} 一致） */
    public int maxSectionY() {
        return this.storage.maxY();
    }

    public void setDirty(final int sectionX, final int sectionY, final int sectionZ, final boolean playerChanged) {
        SectionUpdateTracker.SectionDirtyState section = this.storage.getValue(sectionX, sectionY, sectionZ);
        if (section != null) {
            section.setDirty(playerChanged);
        }
    }

    public void repositionCamera(final SectionPos cameraSectionPos) {
        this.storage.repositionCenter(cameraSectionPos);
    }

    public int size() {
        return this.storage.size();
    }

    public SectionUpdateTracker.@Nullable
            SectionDirtyState getDirtyState(final SectionPos sectionNode) {
        return this.storage.getValue(sectionNode);
    }

    public boolean hasAllNeighbors(final ClientLevel level, final SectionPos sectionNode) {
        return true;
        // return this.doesChunkExistAt(level, sectionNode.offset(Direction.WEST.getStepX(), 0,
        // Direction.WEST.getStepZ()))
        // && this.doesChunkExistAt(level, sectionNode.offset(Direction.NORTH.getStepX(), 0,
        // Direction.NORTH.getStepZ()))
        // && this.doesChunkExistAt(level, sectionNode.offset(Direction.EAST.getStepX(), 0,
        // Direction.EAST.getStepZ()))
        // && this.doesChunkExistAt(level, sectionNode.offset(Direction.SOUTH.getStepX(), 0,
        // Direction.SOUTH.getStepZ()))
        // && this.doesChunkExistAt(level, sectionNode.offset(-1, 0, -1))
        // && this.doesChunkExistAt(level, sectionNode.offset(-1, 0, 1))
        // && this.doesChunkExistAt(level, sectionNode.offset(1, 0, -1))
        // && this.doesChunkExistAt(level, sectionNode.offset(1, 0, 1));
    }

    private boolean doesChunkExistAt(final ClientLevel level, final SectionPos sectionNode) {
        ChunkAccess chunk = level.getChunk(sectionNode.x(), sectionNode.z(), ChunkStatus.FULL, false);
        return chunk != null && level.getLightEngine().lightOnInColumn(SectionPos.of(sectionNode.x(), 0, sectionNode.z()));
    }

    @OnlyIn(Dist.CLIENT)
    public static class SectionDirtyState implements RotatingSectionStorage.Value {
        private boolean isDirty;
        private boolean isDirtyFromPlayer;
        private SectionPos sectionNode;

        private SectionDirtyState(final boolean isDirty, final boolean isDirtyFromPlayer, final SectionPos sectionNode) {
            this.isDirty = isDirty;
            this.isDirtyFromPlayer = isDirtyFromPlayer;
            this.sectionNode = sectionNode;
        }

        public void setDirty(final boolean fromPlayer) {
            boolean wasDirty = this.isDirty;
            this.isDirty = true;
            this.isDirtyFromPlayer = fromPlayer | (wasDirty && this.isDirtyFromPlayer);
        }

        public void setNotDirty() {
            this.isDirty = false;
            this.isDirtyFromPlayer = false;
        }

        @Override
        public void setSectionNode(final SectionPos sectionNode) {
            if (!sectionNode.equals(this.sectionNode)) {
                this.sectionNode = sectionNode;
                this.isDirty = true;
                this.isDirtyFromPlayer = false;
            }
        }

        @Override
        public SectionPos getSectionNode() {
            return this.sectionNode;
        }

        public boolean isDirty() {
            return this.isDirty;
        }

        public boolean isDirtyFromPlayer() {
            return this.isDirtyFromPlayer;
        }
    }
}