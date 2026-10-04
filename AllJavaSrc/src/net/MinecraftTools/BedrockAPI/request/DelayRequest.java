package net.MinecraftTools.BedrockAPI.request;

/**
 * 延时请求 —— 对应 Bedrock 的 {@code DelayRequest} / {@code DelayRequestQueue}。
 *
 * <h2>Bedrock 佐证</h2>
 * {@code paths.txt} 里有对应的测试文件：
 * <pre>
 * src-unittest/DelayRequestUnitTests.cpp
 * src-unittest/DelayRequestQueueUnitTests.cpp
 * src-functest/DelayActionListServerTests.cpp
 * src-functest/DelayRequestServerTests.cpp
 * </pre>
 * 说明 Bedrock 把「等 N Tick 再执行」抽象成<b>一等公民请求类型</b>，
 * 而不是散落的计时器字段。
 *
 * <h2>用途</h2>
 * <ul>
 *   <li>测试替身：验证 {@link RequestRegistry} 的推进/回收逻辑</li>
 *   <li>生产用途：延迟执行副作用（例如「传送后等 2 Tick 再刷新区块窗口」）</li>
 * </ul>
 *
 * @since 2026-09-27
 */
public final class DelayRequest extends Request {

    private final String label;
    private int remaining;
    private Runnable onElapsed;

    public DelayRequest(int delayTicks) {
        this("DelayRequest", delayTicks);
    }

    public DelayRequest(String label, int delayTicks) {
        super(0);
        this.label = label == null ? "DelayRequest" : label;
        this.remaining = Math.max(0, delayTicks);
    }

    /** 到期时执行的回调（可空）。 */
    public DelayRequest onElapsed(Runnable action) {
        this.onElapsed = action;
        return this;
    }

    /** 剩余 Tick。 */
    public int remaining() {
        return remaining;
    }

    @Override
    protected boolean tryAdvance(int tick) {
        if (remaining > 0) {
            remaining--;
        }
        if (remaining > 0) {
            return true;
        }
        if (onElapsed != null) {
            Runnable action = onElapsed;
            onElapsed = null;
            action.run();
        }
        return false;
    }

    @Override
    public String requestName() {
        return label;
    }
}
