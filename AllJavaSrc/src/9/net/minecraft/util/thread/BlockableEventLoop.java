package net.minecraft.util.thread;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.CheckReturnValue;
import net.minecraft.ReportedException;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.util.profiling.metrics.MetricSampler;
import net.minecraft.util.profiling.metrics.MetricsRegistry;
import net.minecraft.util.profiling.metrics.ProfilerMeasured;
import org.slf4j.Logger;

public abstract class BlockableEventLoop<R extends Runnable> implements Executor, TaskScheduler<R>, ProfilerMeasured {
   public static final long BLOCK_TIME_NANOS = 100000L;
   private final String name;
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Queue<R> pendingRunnables = Queues.newConcurrentLinkedQueue();
   private int blockingCount;

   protected BlockableEventLoop(final String name) {
      this.name = name;
      MetricsRegistry.INSTANCE.add(this);
   }

   protected abstract boolean shouldRun(final R task);

   public boolean isSameThread() {
      return Thread.currentThread() == this.getRunningThread();
   }

   protected abstract Thread getRunningThread();

   protected boolean scheduleExecutables() {
      return !this.isSameThread();
   }

   public int getPendingTasksCount() {
      return this.pendingRunnables.size();
   }

   @Override
   public String name() {
      return this.name;
   }

   public <V> CompletableFuture<V> submit(final Supplier<V> supplier) {
      return this.scheduleExecutables() ? CompletableFuture.supplyAsync(supplier, this) : CompletableFuture.completedFuture(supplier.get());
   }

   private CompletableFuture<Void> submitAsync(final Runnable runnable) {
      return CompletableFuture.supplyAsync(() -> {
         runnable.run();
         return null;
      }, this);
   }

   @CheckReturnValue
   public CompletableFuture<Void> submit(final Runnable runnable) {
      if (this.scheduleExecutables()) {
         return this.submitAsync(runnable);
      }

      runnable.run();
      return CompletableFuture.completedFuture(null);
   }

   public void executeBlocking(final Runnable runnable) {
      if (!this.isSameThread()) {
         this.submitAsync(runnable).join();
      } else {
         runnable.run();
      }
   }

   @Override
   public void schedule(final R r) {
      this.pendingRunnables.add(r);
      LockSupport.unpark(this.getRunningThread());
   }

   @Override
   public void execute(final Runnable command) {
      R task = this.wrapRunnable(command);
      if (this.scheduleExecutables()) {
         this.schedule(task);
      } else {
         this.doRunTask(task);
      }
   }

   public void executeIfPossible(final Runnable command) {
      this.execute(command);
   }

   protected void dropAllTasks() {
      this.pendingRunnables.clear();
   }

   protected void runAllTasks() {
      while (this.pollTask()) {
      }
   }

   protected boolean shouldRunAllTasks() {
      return this.blockingCount > 0;
   }

   protected boolean pollTask() {
      R task = this.pendingRunnables.peek();
      if (task == null) {
         return false;
      }

      if (!this.shouldRunAllTasks() && !this.shouldRun(task)) {
         return false;
      }

      this.doRunTask(this.pendingRunnables.remove());
      return true;
   }

   public void managedBlock(final BooleanSupplier condition) {
      this.blockingCount++;

      try {
         while (!condition.getAsBoolean()) {
            if (!this.pollTask()) {
               this.waitForTasks();
            }
         }
      } finally {
         this.blockingCount--;
      }
   }

   protected void waitForTasks() {
      Thread.yield();
      LockSupport.parkNanos("waiting for tasks", 100000L);
   }

   protected void doRunTask(final R task) {
      // $VF: Couldn't be decompiled
      // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
      // java.lang.OutOfMemoryError: Java heap space
      //   at org.jetbrains.java.decompiler.modules.decompiler.flow.DirectNode.forStat(DirectNode.java:39)
      //   at org.jetbrains.java.decompiler.modules.decompiler.flow.FlattenStatementsHelper.createDirectNode(FlattenStatementsHelper.java:92)
      //   at org.jetbrains.java.decompiler.modules.decompiler.flow.FlattenStatementsHelper.flattenStatement(FlattenStatementsHelper.java:156)
      //   at org.jetbrains.java.decompiler.modules.decompiler.flow.FlattenStatementsHelper.flattenStatement(FlattenStatementsHelper.java:478)
      //   at org.jetbrains.java.decompiler.modules.decompiler.flow.FlattenStatementsHelper.flattenStatement(FlattenStatementsHelper.java:181)
      //   at org.jetbrains.java.decompiler.modules.decompiler.flow.FlattenStatementsHelper.flattenStatement(FlattenStatementsHelper.java:474)
      //   at org.jetbrains.java.decompiler.modules.decompiler.flow.FlattenStatementsHelper.flattenStatement(FlattenStatementsHelper.java:474)
      //   at org.jetbrains.java.decompiler.modules.decompiler.flow.FlattenStatementsHelper.buildDirectGraph(FlattenStatementsHelper.java:43)
      //   at org.jetbrains.java.decompiler.modules.decompiler.sforms.SFormsConstructor.splitVariables(SFormsConstructor.java:74)
      //   at org.jetbrains.java.decompiler.modules.decompiler.sforms.SSAUConstructorSparseEx.splitVariables(SSAUConstructorSparseEx.java:43)
      //   at org.jetbrains.java.decompiler.modules.decompiler.StackVarsProcessor.simplifyStackVars(StackVarsProcessor.java:67)
      //   at org.jetbrains.java.decompiler.modules.decompiler.StackVarsProcessor.simplifyStackVars(StackVarsProcessor.java:43)
      //   at org.jetbrains.java.decompiler.main.rels.MethodProcessor.codeToJava(MethodProcessor.java:317)
      //
      // Bytecode:
      // 00: ldc "Task"
      // 02: getstatic net/minecraft/SharedConstants.IS_RUNNING_IN_IDE Z
      // 05: invokestatic com/mojang/jtracy/TracyClient.beginZone (Ljava/lang/String;Z)Lcom/mojang/jtracy/Zone;
      // 08: astore 2
      // 09: aload 1
      // 0a: invokeinterface java/lang/Runnable.run ()V 1
      // 0f: aload 2
      // 10: ifnull 30
      // 13: aload 2
      // 14: invokevirtual com/mojang/jtracy/Zone.close ()V
      // 17: goto 30
      // 1a: astore 3
      // 1b: aload 2
      // 1c: ifnull 2e
      // 1f: aload 2
      // 20: invokevirtual com/mojang/jtracy/Zone.close ()V
      // 23: goto 2e
      // 26: astore 4
      // 28: aload 3
      // 29: aload 4
      // 2b: invokevirtual java/lang/Throwable.addSuppressed (Ljava/lang/Throwable;)V
      // 2e: aload 3
      // 2f: athrow
      // 30: goto 4f
      // 33: astore 2
      // 34: getstatic net/minecraft/util/thread/BlockableEventLoop.LOGGER Lorg/slf4j/Logger;
      // 37: getstatic com/mojang/logging/LogUtils.FATAL_MARKER Lorg/slf4j/Marker;
      // 3a: ldc "Error executing task on {}"
      // 3c: aload 0
      // 3d: invokevirtual net/minecraft/util/thread/BlockableEventLoop.name ()Ljava/lang/String;
      // 40: aload 2
      // 41: invokeinterface org/slf4j/Logger.error (Lorg/slf4j/Marker;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V 5
      // 46: aload 2
      // 47: invokestatic net/minecraft/util/thread/BlockableEventLoop.isNonRecoverable (Ljava/lang/Throwable;)Z
      // 4a: ifeq 4f
      // 4d: aload 2
      // 4e: athrow
      // 4f: return
      // try (4 -> 6): 11 java/lang/Throwable
      // try (14 -> 16): 17 java/lang/Throwable
      // try (0 -> 23): 24 java/lang/Exception
   }

   @Override
   public List<MetricSampler> profiledMetrics() {
      return ImmutableList.of(MetricSampler.create(this.name + "-pending-tasks", MetricCategory.EVENT_LOOPS, this::getPendingTasksCount));
   }

   public static boolean isNonRecoverable(final Throwable t) {
      return t instanceof ReportedException r ? isNonRecoverable(r.getCause()) : t instanceof OutOfMemoryError || t instanceof StackOverflowError;
   }
}
