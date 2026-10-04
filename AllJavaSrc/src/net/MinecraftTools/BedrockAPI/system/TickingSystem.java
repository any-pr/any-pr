package net.MinecraftTools.BedrockAPI.system;

/**
 * ECS 系统 —— 对应 Bedrock 的 {@code ITickingSystem} / {@code *System} 系列。
 *
 * <h2>Bedrock 实证签名</h2>
 * <pre>
 * CameraActivationSystem::tick(EntityRegistry&amp;)
 * CameraAvoidanceSystem::tick(EntityRegistry&amp;)
 * CameraBobSystem::tick(EntityRegistry&amp;)
 * CameraAspectRatioSystem::tick(EntityRegistry&amp;)
 * CameraAttachSystem::tick(EntityRegistry&amp;)
 * </pre>
 * 统一形态：{@code tick(registry)}，无返回值，内部自行遍历匹配的组件集合。
 *
 * <h2>命名规则（强制）</h2>
 * 实现类一律以 {@code System} 结尾，且<b>必须有</b> {@code tick} 方法。
 * 纯工具逻辑不要叫 {@code System} —— 用 {@code Util}（私有方法加 {@code _} 前缀）。
 *
 * <p><b>为何不叫 {@code System}？</b>避免与 {@code java.lang.System} 冲突，
 * 同时对齐 Bedrock 的 {@code ITickingSystem} 命名。
 *
 * @since 2026-09-27
 */
public interface TickingSystem {

    /**
     * 每 Tick 调用一次。
     *
     * @param registry 系统注册表（可访问本 Tick 的请求队列、组件视图等）
     */
    void tick(SystemRegistry registry);

    /**
     * 执行顺序 —— 数值小的先跑。
     *
     * <p>对应 Bedrock 的 {@code StrictTickingSystem} 分层：
     * 输入 → 移动 → 碰撞 → 传送 → 网络 → 渲染数据准备。
     */
    default int order() {
        return 0;
    }

    /** 系统名（默认取实现类简单名）。 */
    default String systemName() {
        return getClass().getSimpleName();
    }

    /** 是否启用（配合 {@link net.MinecraftTools.BedrockAPI.toggle.FeatureToggles}）。 */
    default boolean isEnabled() {
        return true;
    }
}
