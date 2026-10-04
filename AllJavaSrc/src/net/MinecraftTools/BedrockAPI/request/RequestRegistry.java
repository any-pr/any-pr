package net.MinecraftTools.BedrockAPI.request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 请求注册表 —— 对应 Bedrock 里「请求组件 + 系统轮询」的宿主容器。
 *
 * <h2>Bedrock 对照</h2>
 * <pre>
 * RequestPlayerChangeDimension(registry, eid)              → {@link #add(Request)}
 * TryHandleChangeDimensionRequestLevel(...)                → {@link #tick(int)}
 * PlayerToNewDimensionLevelNotComplete_..._RequestRetained → tick 后请求仍在队列
 * PlayerToNewDimensionLevelComplete_..._RequestRemoved     → tick 后请求被回收
 * PlayerWithChangeDimensionRequest_LeavesGame_..._IsRemoved→ {@link #cancelAll(Predicate)}
 * </pre>
 *
 * <h2>线程模型</h2>
 * 与 Bedrock 一致：<b>只在主线程 tick</b>。跨线程投递请走
 * {@link #submit(Request)}（内部同步块），tick 时统一消费。
 *
 * @since 2026-09-27
 */
public final class RequestRegistry {

    private final List<Request> pending = new ArrayList<>();
    private final List<Request> incoming = new ArrayList<>();
    private final List<Request> completed = new ArrayList<>();

    private long totalSubmitted;
    private long totalCompleted;
    private long totalFailed;

    /** 跨线程投递（下一 Tick 生效）。 */
    public void submit(Request request) {
        Objects.requireNonNull(request, "request");
        synchronized (incoming) {
            incoming.add(request);
            totalSubmitted++;
        }
    }

    /** 立即加入（仅主线程调用）。 */
    public void add(Request request) {
        Objects.requireNonNull(request, "request");
        pending.add(request);
        totalSubmitted++;
    }

    /**
     * 推进所有请求 —— 由 {@link net.MinecraftTools.BedrockAPI.system.SystemRegistry} 每 Tick 调用。
     *
     * @param tick 当前 Tick 计数
     * @return 本 Tick 完成的请求列表（供调用方做后续副作用）
     */
    public List<Request> tick(int tick) {
        // 1. 消费跨线程投递
        synchronized (incoming) {
            if (!incoming.isEmpty()) {
                pending.addAll(incoming);
                incoming.clear();
            }
        }

        // 2. 推进
        completed.clear();
        pending.removeIf(request -> {
            boolean keep = request.advance(tick);
            if (!keep) {
                completed.add(request);
                if (request.state() == RequestState.COMPLETED) {
                    totalCompleted++;
                } else {
                    totalFailed++;
                }
            }
            return !keep;
        });

        return Collections.unmodifiableList(new ArrayList<>(completed));
    }

    /** 取消匹配的请求（例如实体离场）。 */
    public int cancelAll(Predicate<Request> filter) {
        Objects.requireNonNull(filter, "filter");
        int n = 0;
        for (Request request : pending) {
            if (filter.test(request) && !request.state().isTerminal()) {
                request.cancel();
                n++;
            }
        }
        pending.removeIf(r -> r.state().isTerminal());
        return n;
    }

    /** 当前等待中的请求数（含跨线程待投递）。 */
    public int size() {
        synchronized (incoming) {
            return pending.size() + incoming.size();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /** 只读快照。 */
    public List<Request> snapshot() {
        synchronized (incoming) {
            List<Request> all = new ArrayList<>(pending.size() + incoming.size());
            all.addAll(pending);
            all.addAll(incoming);
            return Collections.unmodifiableList(all);
        }
    }

    public long totalSubmitted() {
        return totalSubmitted;
    }

    public long totalCompleted() {
        return totalCompleted;
    }

    public long totalFailed() {
        return totalFailed;
    }

    /** 清空（世界卸载 / 玩家离场）。 */
    public void clear() {
        synchronized (incoming) {
            incoming.clear();
        }
        for (Request request : pending) {
            request.cancel();
        }
        pending.clear();
    }

    @Override
    public String toString() {
        return "RequestRegistry{pending=" + size()
                + ", submitted=" + totalSubmitted
                + ", completed=" + totalCompleted
                + ", failed=" + totalFailed + "}";
    }

    public static void main(String[] args) {
        System.out.println("=== RequestRegistry 测试 ===");

        RequestRegistry registry = new RequestRegistry();
        registry.add(new DelayRequest(2));
        registry.add(new DelayRequest(5));
        registry.submit(new DelayRequest(1)); // 跨线程投递
        System.out.println("提交后      = " + registry);

        for (int tick = 0; tick < 8; tick++) {
            List<Request> done = registry.tick(tick);
            System.out.println("tick " + tick + "     = " + registry
                    + "  本Tick完成=" + done.size());
        }

        System.out.println("最终        = " + registry);
        if (!registry.isEmpty()) {
            throw new AssertionError("队列应已清空: " + registry);
        }
        if (registry.totalCompleted() != 3) {
            throw new AssertionError("应完成 3 个: " + registry.totalCompleted());
        }

        // 取消路径（实体离场）
        RequestRegistry r2 = new RequestRegistry();
        r2.add(new DelayRequest(100));
        r2.add(new DelayRequest(100));
        int cancelled = r2.cancelAll(r -> r instanceof DelayRequest);
        System.out.println("取消数      = " + cancelled + ", " + r2);
        if (cancelled != 2 || !r2.isEmpty()) {
            throw new AssertionError("取消逻辑有误: " + r2);
        }

        System.out.println("全部 PASS");
    }
}
