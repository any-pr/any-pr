package net.MinecraftTools.BedrockAPI.system;

/**
 * 组件定义 —— 对应 Bedrock 的 {@code *Definition} 系列
 * （{@code CameraAttachDefinition} / {@code CameraAvoidanceDefinition} / ...）。
 *
 * <h2>Bedrock 实证签名</h2>
 * <pre>
 * CameraAttachDefinition::bindType()
 * CameraAttachDefinition::initialize(EntityContext&amp;, CameraAttachComponent&amp;) const
 * CameraAvoidanceDefinition::bindType()
 * CameraAvoidanceDefinition::initialize(EntityContext&amp;, CameraAvoidanceComponent&amp;) const
 * </pre>
 * 每个 {@code Definition} 都是<b>静态注册</b>到 ECS 类型系统里的：
 * {@code bindType()} 在启动时跑一次，{@code initialize()} 在实体挂载组件时跑。
 *
 * <h2>命名规则（强制）</h2>
 * 实现类名 = 组件名去掉 {@code Component} 换成 {@code Definition}。
 * 例：{@code ChunkWindowComponent} ↔ {@code ChunkWindowDefinition}。
 *
 * @param <C> 该定义负责的组件类型
 * @since 2026-09-27
 */
public interface ComponentDefinition<C extends Component> {

    /**
     * 注册类型 —— 启动时调用一次。
     *
     * <p>对应 Bedrock 的 {@code bindType()}：把组件类型登记进 ECS 注册表
     * （序列化 ID、网络同步标记、调试验证器）。
     */
    void bindType();

    /**
     * 从定义数据初始化组件 —— 挂载时调用。
     *
     * <p>对应 Bedrock 的 {@code initialize(EntityContext&amp;, Component&amp;) const}。
     *
     * @param context 宿主上下文（实体/区块/注册表句柄，由调用方决定具体类型）
     * @param component 待初始化的组件实例
     */
    void initialize(Object context, C component);

    /** 该定义负责的组件类型。 */
    Class<C> componentType();

    /** 定义名（默认取实现类简单名，去掉 {@code Definition} 后缀）。 */
    default String definitionName() {
        String simple = getClass().getSimpleName();
        return simple.endsWith("Definition")
                ? simple.substring(0, simple.length() - "Definition".length())
                : simple;
    }
}
