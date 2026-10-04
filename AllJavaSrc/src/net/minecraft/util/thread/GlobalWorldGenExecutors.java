package net.minecraft.util.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 🔧 MCRe（C2ME A2 简化版移植）：世界生成全局并行池。
 * 26.2 原版：ChunkTaskDispatcher 的任务经 ConsecutiveExecutor 一次一个串行执行（全局串行）；
 * 简化版：生成批直接提交到此池（配合 WorldGenLocks 写入半径锁）→ 不同 chunk 的生成任务并行。
 * 线程 NORM_PRIORITY-1（C2ME 同款，不与游戏主逻辑抢调度）。
 */
public class GlobalWorldGenExecutors {
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    public static final int PARALLELISM = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(PARALLELISM, runnable -> {
        final Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        thread.setName("MCRe-worldgen-%d".formatted(COUNTER.getAndIncrement()));
        return thread;
    });

    private GlobalWorldGenExecutors() {
    }

    public static void execute(final Runnable task) {
        POOL.execute(task);
    }
}