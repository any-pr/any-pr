package net.minecraft.server.level;

import java.util.concurrent.CompletableFuture;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

public interface GeneratingChunkMap {
    @Deprecated
    GenerationChunkHolder acquireGeneration(long chunkNode);

    void releaseGeneration(GenerationChunkHolder chunkHolder);

    // ===== MCRe: 手动编码，移除对 toLong() 的依赖 =====
    default GenerationChunkHolder acquireGeneration(ChunkPos pos) {
        long key = (pos.x << 32) | (pos.z & 0xFFFFFFFFL);
        return this.acquireGeneration(key);
    }
    // ===== MCRe 结束 =====

    CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder chunkHolder, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache);

    ChunkGenerationTask scheduleGenerationTask(ChunkStatus targetStatus, ChunkPos pos);

    void runGenerationTasks();
}