package net.MinecraftTools.BedrockAPI.system;

/**
 * ECS 组件标记 —— 对应 Bedrock 的 {@code *Component} 系列
 * （{@code CameraAttachComponent} / {@code PlayerChangeDimensionRequestComponent} / ...）。
 *
 * <p>Bedrock 的组件是 POD 式纯数据 + 由 {@code Definition} 绑定类型与生命周期。
 * 本接口保持同样定位：<b>只放数据，不放逻辑</b>；行为写在 {@link TickingSystem} 里。
 *
 * <h2>命名规则（强制）</h2>
 * 实现类一律以 {@code Component} 结尾，且<b>必须</b>配套一个 {@code XxxDefinition}
 * （见 {@link ComponentDefinition}），负责：
 * <ul>
 *   <li>{@code bindType()} —— 注册类型（序列化 ID / 网络 ID / 调试验证）</li>
 *   <li>{@code initialize()} —— 从定义数据初始化组件字段</li>
 * </ul>
 *
 * <h2>Bedrock 佐证</h2>
 * 符号表里 {@code EntityRegistryBase::_addComponent<CameraAttachComponent>(EntityId)}
 * 这类模板实例化有数千条 —— 说明组件注册走的是统一的模板入口，不是手写 setter。
 *
 * @since 2026-09-27
 */
public interface Component {

    /**
     * 组件是否处于「活」状态。
     *
     * <p>默认恒为 {@code true}；带生命周期的组件（如 {@code Request}）可覆写，
     * 让 {@link SystemRegistry} 在一轮 tick 后回收死组件。
     */
    default boolean isAlive() {
        return true;
    }

    /**
     * 组件类型名（默认取实现类简单名）。
     *
     * <p>Bedrock 用 {@code Bedrock::typeid_t<T>::_getCounter()} 做编译期类型 ID；
     * Java 侧用类名字符串做等价物，供调试与序列化使用。
     */
    default String componentTypeName() {
        return getClass().getSimpleName();
    }
}
