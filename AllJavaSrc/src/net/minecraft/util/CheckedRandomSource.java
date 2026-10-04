package net.minecraft.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🔧 MCRe（C2ME 多线程地基移植）：带线程属主检查的 RandomSource。
 * 检测异步线程（结构生成 / 分带填充等）乱用世界随机实例 —— 防"异步线程乱改共享状态"（tick 队列 NPE 同款病根）。
 * 不安全调用自动切 FALLBACK（每线程独立实例，vanilla 单线程语义零变化），日志去重报警，不崩游戏。
 */
public class CheckedRandomSource implements RandomSource {
    private static final Logger LOGGER = LoggerFactory.getLogger("CheckedRandomSource");
    private static final ThreadLocal<RandomSource> FALLBACK = ThreadLocal.withInitial(RandomSource::create);
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private final RandomSource delegate;
    private final Supplier<Thread> owner;

    public CheckedRandomSource(final RandomSource delegate, final Supplier<Thread> owner) {
        this.delegate = delegate;
        this.owner = owner;
    }

    private boolean isSafe() {
        final Thread ownerThread = this.owner.get();
        return ownerThread == null || Thread.currentThread() == ownerThread;
    }

    private void handleNotOwner() {
        final Thread ownerThread = this.owner.get();
        final String message = "World random accessed from a different thread (owner: %s, current: %s)".formatted(
            ownerThread == null ? "unknown" : ownerThread.getName(), Thread.currentThread().getName()
        );
        if (REPORTED.add(message)) {
            LOGGER.error(message, new java.util.ConcurrentModificationException(message));
        }
    }

    private RandomSource active() {
        if (!this.isSafe()) {
            this.handleNotOwner();
            return FALLBACK.get();
        }
        return this.delegate;
    }

    @Override
    public RandomSource fork() {
        return this.active().fork();
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return this.active().forkPositional();
    }

    @Override
    public void setSeed(final long seed) {
        this.active().setSeed(seed);
    }

    @Override
    public int nextInt() {
        return this.active().nextInt();
    }

    @Override
    public int nextInt(final int bound) {
        return this.active().nextInt(bound);
    }

    @Override
    public long nextLong() {
        return this.active().nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return this.active().nextBoolean();
    }

    @Override
    public float nextFloat() {
        return this.active().nextFloat();
    }

    @Override
    public double nextDouble() {
        return this.active().nextDouble();
    }

    @Override
    public double nextGaussian() {
        return this.active().nextGaussian();
    }
}