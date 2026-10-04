package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.PriorityConsecutiveExecutor;
import net.minecraft.util.thread.StrictQueue;
import net.minecraft.util.thread.GlobalWorldGenExecutors;
import net.minecraft.util.thread.TaskScheduler;
import net.minecraft.util.thread.WorldGenLocks;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ChunkTaskDispatcher implements ChunkHolder.LevelChangeListener, AutoCloseable {
    public static final int DISPATCHER_PRIORITY_COUNT = 4;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ChunkTaskPriorityQueue queue;
    private final TaskScheduler<Runnable> executor;
    private final PriorityConsecutiveExecutor dispatcher;
    protected boolean sleeping;
    private final boolean parallel;

    public ChunkTaskDispatcher(final TaskScheduler<Runnable> executor, final Executor dispatcherExecutor, final boolean parallel) {
        this.queue = new ChunkTaskPriorityQueue(executor.name() + "_queue");
        this.executor = executor;
        this.dispatcher = new PriorityConsecutiveExecutor(4, dispatcherExecutor, "dispatcher");
        this.sleeping = true;
        this.parallel = parallel;
    }

    public boolean hasWork() {
        return this.dispatcher.hasWork() || this.queue.hasWork();
    }

    @Override
    public void onLevelChange(final ChunkPos pos, final IntSupplier oldLevel, final int newLevel, final IntConsumer setQueueLevel) {
        this.dispatcher.schedule(new StrictQueue.RunnableWithPriority(0, () -> {
            int oldTicketLevel = oldLevel.getAsInt();
            if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
                LOGGER.debug("RES {} {} -> {}", pos, oldTicketLevel, newLevel);
            }

            this.queue.resortChunkTasks(oldTicketLevel, pos, newLevel);
            setQueueLevel.accept(newLevel);
        }));
    }

    public void release(final ChunkPos pos, final Runnable whenReleased, final boolean clearQueue) {
        this.dispatcher.schedule(new StrictQueue.RunnableWithPriority(1, () -> {
            this.queue.release(pos, clearQueue);
            this.onRelease(pos);
            if (this.sleeping) {
                this.sleeping = false;
                this.pollTask();
            }

            whenReleased.run();
        }));
    }

    public void submit(final Runnable task, final ChunkPos pos, final IntSupplier level) {
        this.dispatcher.schedule(new StrictQueue.RunnableWithPriority(2, () -> {
            int ticketLevel = level.getAsInt();
            if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
                LOGGER.debug("SUB {} {} {} {}", pos, ticketLevel, this.executor, this.queue);
            }

            this.queue.submit(task, pos, ticketLevel);
            if (this.sleeping) {
                this.sleeping = false;
                this.pollTask();
            }
        }));
    }

    protected void pollTask() {
        this.dispatcher.schedule(new StrictQueue.RunnableWithPriority(3, () -> {
            ChunkTaskPriorityQueue.TasksForChunk tasksForChunk = this.popTasks();
            if (tasksForChunk == null) {
                this.sleeping = true;
            } else {
                this.scheduleForExecution(tasksForChunk);
            }
        }));
    }

    protected void scheduleForExecution(final ChunkTaskPriorityQueue.TasksForChunk tasksForChunk) {
        if (!this.parallel) {
            // 原版串行链：light dispatcher 保持串行（光照引擎共享状态非线程安全，并行会竞争）
            CompletableFuture.allOf(tasksForChunk.tasks().stream().map(message -> this.executor.scheduleWithResult(future -> {
                message.run();
                future.complete(Unit.INSTANCE);
            })).toArray(CompletableFuture[]::new)).thenAccept(r -> this.pollTask());
            return;
        }

        // 🔧 MCRe（C2ME A2 简化版移植）：破除批间串行 —— 批提交到全局并行池（写入半径锁互斥：
        // 同 chunk 跨批串行 + 结构 piece 跨 chunk 写入防竞争），立即 pollTask 补货（队列驱动无栈递归）
        // → 多个 chunk 的生成任务并行执行。原版：allOf 等批完成 + ConsecutiveExecutor 一次一个 = 全局串行。
        final long lockCenterX = tasksForChunk.chunkPos().x();
        final long lockCenterZ = tasksForChunk.chunkPos().z();
        GlobalWorldGenExecutors.execute(() -> WorldGenLocks.runLocked(lockCenterX, lockCenterZ, () -> {
            for (Runnable task : tasksForChunk.tasks()) {
                task.run();
            }
        }));
        this.pollTask();
    }

    protected void onRelease(final ChunkPos key) {
    }

    protected ChunkTaskPriorityQueue.@Nullable TasksForChunk popTasks() {
        return this.queue.pop();
    }

    @Override
    public void close() {
        this.executor.close();
    }
}