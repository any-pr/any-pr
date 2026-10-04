package net.minecraft.server.level;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;

public abstract class ChunkTracker extends DynamicGraphMinFixedPoint {
    // ===== MCRe: 使用 Long.MIN_VALUE 作为无效哨兵（避免依赖已废弃的常量） =====
    private static final long INVALID_CHUNK_POS = Long.MIN_VALUE;

    protected ChunkTracker(final int levelCount, final int minQueueSize, final int minMapSize) {
        super(levelCount, minQueueSize, minMapSize);
    }

    @Override
    protected boolean isSource(final long node) {
        return node == INVALID_CHUNK_POS;
    }

    @Override
    protected void checkNeighborsAfterUpdate(final long node, final int level, final boolean onlyDecrease) {
        if (!onlyDecrease || level < this.levelCount - 2) {
            ChunkPos pos = new ChunkPos(node);
            long x = pos.x;
            long z = pos.z;

            for (long offsetX = -1; offsetX <= 1; offsetX++) {
                for (long offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    // ===== MCRe: 直接用 ChunkPos 构造 + 手动编码，不调用 asLong/toLong =====
                    long neighbor = ((x + offsetX) << 32) | (z + offsetZ & 0xFFFFFFFFL);
                    if (neighbor != node) {
                        this.checkNeighbor(node, neighbor, level, onlyDecrease);
                    }
                }
            }
        }
    }

    @Override
    protected int getComputedLevel(final long node, final long knownParent, final int knownLevelFromParent) {
        int computedLevel = knownLevelFromParent;
        ChunkPos pos = new ChunkPos(node);
        long x = pos.x;
        long z = pos.z;

        for (long offsetX = -1; offsetX <= 1; offsetX++) {
            for (long offsetZ = -1; offsetZ <= 1; offsetZ++) {
                // ===== MCRe: 手动编码邻居键 =====
                long neighbor = ((x + offsetX) << 32) | (z + offsetZ & 0xFFFFFFFFL);
                if (neighbor == node) {
                    neighbor = INVALID_CHUNK_POS;
                }

                if (neighbor != knownParent) {
                    int costFromNeighbor = this.computeLevelFromNeighbor(neighbor, node, this.getLevel(neighbor));
                    if (computedLevel > costFromNeighbor) {
                        computedLevel = costFromNeighbor;
                    }

                    if (computedLevel == 0) {
                        return computedLevel;
                    }
                }
            }
        }

        return computedLevel;
    }

    @Override
    protected int computeLevelFromNeighbor(final long from, final long to, final int fromLevel) {
        return from == INVALID_CHUNK_POS ? this.getLevelFromSource(to) : fromLevel + 1;
    }

    protected abstract int getLevelFromSource(long to);

    public void update(final long node, final int newLevelFrom, final boolean onlyDecreased) {
        this.checkEdge(INVALID_CHUNK_POS, node, newLevelFrom, onlyDecreased);
    }
}