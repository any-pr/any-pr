package net.MinecraftTools.BedrockAPI.request;

import net.MinecraftTools.BedrockAPI.system.Component;

import java.util.Objects;

/**
 * 异步请求基类 —— 对应 Bedrock 的 {@code *RequestComponent} 模式。
 *
 * <h2>为什么必须异步（Bedrock 铁律）</h2>
 * Bedrock 的符号表里<b>找不到任何同步跨维度传送</b>：
 * <pre>
 * PlayerChangeDimensionRequestComponent      ← 请求以组件形式挂在实体上
 * RequestPlayerChangeDimension(...)          ← 只是把请求塞进 registry
 * TryHandleChangeDimensionRequestLevel(...)  ← 系统每 Tick 尝试推进
 * TryHandleChangeDimensionRequestLevel_RequestRetained   ← 目标没加载好就继续留着
 * </pre>
 * 同一套模式也适用于 MCRe 的「传送 / 窗口移动 / 区块窗口跟随」：
 * <b>请求方只登记意图，落地由系统在目标就绪后完成。</b>
 *
 * <h2>生命周期</h2>
 * <pre>
 * new XxxRequest(...)            PENDING
 *   ↓ SystemRegistry 每 Tick 调 tryAdvance()
 *   ↓ 返回 true  →  IN_PROGRESS（继续等待）
 *   ↓ 返回 false →  COMPLETED   （系统回收）
 * 超时 / 目标非法 →  FAILED
 * 实体离场        →  CANCELLED
 * </pre>
 *
 * <h2>命名规则（强制）</h2>
 * 实现类以 {@code Request} 结尾；若作为 ECS 组件使用，另配
 * {@code XxxRequestComponent} + {@code XxxRequestDefinition}。
 *
 * @since 2026-09-27
 */
public abstract class Request implements Component {

    private RequestState state = RequestState.PENDING;
    private String failureReason;
    private int ageTicks;
    private final int timeoutTicks;

    protected Request() {
        this(0);
    }

    /**
     * @param timeoutTicks 超时 Tick 数；{@code <= 0} 表示不超时
     */
    protected Request(int timeoutTicks) {
        this.timeoutTicks = Math.max(0, timeoutTicks);
    }

    /**
     * 推进请求 —— 由 {@link RequestRegistry} 每 Tick 调用。
     *
     * @param tick 当前 Tick 计数
     * @return {@code true} 表示仍需保留（继续等待）；{@code false} 表示已完成，可回收
     */
    protected abstract boolean tryAdvance(int tick);

    /**
     * 被注册表驱动一步（模板方法，子类覆写 {@link #tryAdvance(int)}）。
     *
     * @return 是否仍需保留
     */
    public final boolean advance(int tick) {
        ageTicks++;
        if (state.isTerminal()) {
            return false;
        }
        if (timeoutTicks > 0 && ageTicks > timeoutTicks) {
            fail("timeout after " + ageTicks + " ticks (limit " + timeoutTicks + ")");
            return false;
        }
        if (state == RequestState.PENDING) {
            state = RequestState.IN_PROGRESS;
        }
        boolean keep;
        try {
            keep = tryAdvance(tick);
        } catch (RuntimeException e) {
            fail(e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
        if (!keep) {
            complete();
        }
        return keep;
    }

    /** 标记完成。 */
    public final void complete() {
        if (!state.isTerminal()) {
            state = RequestState.COMPLETED;
        }
    }

    /** 标记失败。 */
    public final void fail(String reason) {
        if (!state.isTerminal()) {
            state = RequestState.FAILED;
            failureReason = reason;
        }
    }

    /** 标记取消（实体离场 / 上游撤销）。 */
    public final void cancel() {
        if (!state.isTerminal()) {
            state = RequestState.CANCELLED;
        }
    }

    public final RequestState state() {
        return state;
    }

    /** 失败原因；非失败态返回 {@code null}。 */
    public final String failureReason() {
        return failureReason;
    }

    /** 已存活 Tick 数。 */
    public final int ageTicks() {
        return ageTicks;
    }

    /** 超时上限（0 = 不超时）。 */
    public final int timeoutTicks() {
        return timeoutTicks;
    }

    /** 请求名（默认取实现类简单名）。 */
    public String requestName() {
        return getClass().getSimpleName();
    }

    @Override
    public final boolean isAlive() {
        return !state.isTerminal();
    }

    @Override
    public String toString() {
        String base = requestName() + "{" + state + ", age=" + ageTicks;
        if (timeoutTicks > 0) {
            base += "/" + timeoutTicks;
        }
        if (failureReason != null) {
            base += ", reason=" + failureReason;
        }
        return base + "}";
    }

    public static void main(String[] args) {
        System.out.println("=== Request 测试 ===");

        DelayRequest r = new DelayRequest(3);
        System.out.println("初始        = " + r);
        int t = 0;
        while (r.advance(t++)) {
            System.out.println("tick " + t + "    = " + r);
        }
        System.out.println("结束        = " + r);
        if (r.state() != RequestState.COMPLETED) {
            throw new AssertionError("应完成: " + r);
        }

        // 超时
        Request timeout = new Request(2) {
            @Override
            protected boolean tryAdvance(int tick) {
                return true;
            }
        };
        timeout.advance(0);
        timeout.advance(1);
        boolean keep = timeout.advance(2);
        System.out.println("超时        = " + timeout + ", keep=" + keep);
        if (keep || timeout.state() != RequestState.FAILED) {
            throw new AssertionError("应超时失败: " + timeout);
        }

        // 异常捕获
        Request boom = new Request(0) {
            @Override
            protected boolean tryAdvance(int tick) {
                throw new IllegalStateException("boom");
            }
        };
        boom.advance(0);
        System.out.println("异常        = " + boom);
        if (boom.state() != RequestState.FAILED) {
            throw new AssertionError("异常应转 FAILED: " + boom);
        }

        // 取消
        Request cancel = new DelayRequest(100);
        cancel.cancel();
        System.out.println("取消        = " + cancel + ", isAlive=" + cancel.isAlive());
        if (cancel.isAlive()) {
            throw new AssertionError("取消后不应存活");
        }

        System.out.println("全部 PASS");
    }
}
