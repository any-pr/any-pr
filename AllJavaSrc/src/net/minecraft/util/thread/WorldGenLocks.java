package net.minecraft.util.thread;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 🔧 MCRe（C2ME A2 简化版移植）：世界生成写入半径锁。
 * 同一 chunk 区域（±LOCK_RADIUS）互斥、不同区域并行；锁按坐标排序获取 → 无循环等待 → 无死锁。
 * 覆盖两类竞争：① 并行化后同一 chunk 的跨批任务互斥 ② 结构 piece 跨 chunk 写入邻域 ProtoChunk 防竞争。
 * 锁不回收（每 chunk 一把 ≈ 数十字节，活跃区块数万 = 几 MB，换取简单正确）。
 */
public class WorldGenLocks {
    private static final int LOCK_RADIUS = 2;
    private static final ConcurrentMap<Long, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private WorldGenLocks() {
    }

    private static ReentrantLock lockFor(final long chunkX, final long chunkZ) {
        // 区块坐标在 int 范围内（±1.87 亿 < 2^31），组合键安全
        return LOCKS.computeIfAbsent((chunkX << 32) | (chunkZ & 0xFFFFFFFFL), key -> new ReentrantLock());
    }

    public static void runLocked(final long centerX, final long centerZ, final Runnable task) {
        final List<ReentrantLock> held = new ArrayList<>();
        try {
            // 固定顺序获取（先 chunkX 后 chunkZ 排序）→ 无循环等待 → 无死锁
            final List<long[]> coords = new ArrayList<>();
            for (long dx = -LOCK_RADIUS; dx <= LOCK_RADIUS; dx++) {
                for (long dz = -LOCK_RADIUS; dz <= LOCK_RADIUS; dz++) {
                    coords.add(new long[]{centerX + dx, centerZ + dz});
                }
            }

            coords.sort(Comparator.<long[]>comparingLong(c -> c[0]).thenComparingLong(c -> c[1]));
            for (long[] coord : coords) {
                final ReentrantLock lock = lockFor(coord[0], coord[1]);
                lock.lock();
                held.add(lock);
            }

            task.run();
        } finally {
            for (int i = held.size() - 1; i >= 0; i--) {
                held.get(i).unlock();
            }
        }
    }
}