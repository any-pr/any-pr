package net.MinecraftTools.BedrockAPI.system;

import net.MinecraftTools.BedrockAPI.request.DelayRequest;
import net.MinecraftTools.BedrockAPI.request.Request;
import net.MinecraftTools.BedrockAPI.request.RequestRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统注册表 —— ECS 的调度中枢，对应 Bedrock 的 {@code EntityRegistry} + {@code EntitySystemsCollection}。
 *
 * <h2>Bedrock 对照</h2>
 * <pre>
 * EntitySystemsCollection::tick(...)   → {@link #tick(int)}
 * EntityRegistryBase::addComponent&lt;T&gt;(EntityId) → {@link #components(Class)}
 * CameraActivationSystem::tick(EntityRegistry&amp;)     → {@link TickingSystem#tick(SystemRegistry)}
 * </pre>
 *
 * <h2>职责</h2>
 * <ol>
 *   <li>按 {@link TickingSystem#order()} 排序后依次 tick（对应 Bedrock 的分层系统顺序）</li>
 *   <li>持有 {@link RequestRegistry}，请求推进也发生在同一 Tick 内</li>
 *   <li>按类型持有组件实例（Bedrock 的 {@code _addComponent<T>} 等价入口）
 *       —— <b>刻意不做完整 ECS 实现</b>，只提供 Bedrock 风格的注册/查询契约</li>
 * </ol>
 *
 * @since 2026-09-27
 */
public final class SystemRegistry {

    private final List<TickingSystem> systems = new ArrayList<>();
    private final RequestRegistry requests = new RequestRegistry();
    private final Map<Class<?>, List<Component>> componentStore = new ConcurrentHashMap<>();
    private final Map<Class<?>, ComponentDefinition<?>> definitions = new ConcurrentHashMap<>();

    private int tickCounter;
    private boolean frozen;

    // ------------------------------------------------------------------
    // 注册
    // ------------------------------------------------------------------

    /** 注册一个系统（按 {@link TickingSystem#order()} 自动排序）。 */
    public void register(TickingSystem system) {
        Objects.requireNonNull(system, "system");
        if (frozen) {
            throw new IllegalStateException("registry frozen; register systems before first tick");
        }
        systems.add(system);
        systems.sort(Comparator.comparingInt(TickingSystem::order));
    }

    /** 注册组件定义 —— 对应 Bedrock 的 {@code Definition::bindType()}。 */
    public void registerDefinition(ComponentDefinition<?> definition) {
        Objects.requireNonNull(definition, "definition");
        definition.bindType();
        definitions.put(definition.componentType(), definition);
    }

    /**
     * 挂载组件 —— 对应 Bedrock 的
     * {@code EntityRegistryBase::_addComponent<T>(EntityId)}。
     *
     * <p>若该组件类型注册过 {@code Definition}，会自动调用其
     * {@code initialize(context, component)}。
     */
    @SuppressWarnings("unchecked")
    public <C extends Component> C addComponent(C component, Object context) {
        Objects.requireNonNull(component, "component");
        Class<?> type = component.getClass();
        componentStore.computeIfAbsent(type, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(component);

        ComponentDefinition<?> definition = definitions.get(type);
        if (definition != null) {
            ((ComponentDefinition<C>) definition).initialize(context, component);
        }
        return component;
    }

    /** 按类型取组件视图（Bedrock 的 {@code View<...>}）。 */
    @SuppressWarnings("unchecked")
    public <C extends Component> List<C> components(Class<C> type) {
        List<Component> list = componentStore.get(type);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf((List<C>) (List<?>) list);
        }
    }

    /** 回收所有 {@code !isAlive()} 的组件。 */
    public int reapDeadComponents() {
        int n = 0;
        for (List<Component> list : componentStore.values()) {
            synchronized (list) {
                int before = list.size();
                list.removeIf(c -> !c.isAlive());
                n += before - list.size();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------
    // 调度
    // ------------------------------------------------------------------

    /** 系统数量。 */
    public int systemCount() {
        return systems.size();
    }

    public List<TickingSystem> systems() {
        return List.copyOf(systems);
    }

    public RequestRegistry requests() {
        return requests;
    }

    public int tickCounter() {
        return tickCounter;
    }

    /**
     * 推进一个 Tick。
     *
     * <p>顺序（对齐 Bedrock 的分层）：
     * <ol>
     *   <li>各系统 {@code tick(this)}（按 order 升序）</li>
     *   <li>请求推进（含跨线程投递消费）</li>
     *   <li>死组件回收</li>
     * </ol>
     *
     * @return 本 Tick 完成的请求
     */
    public List<Request> tick() {
        frozen = true;
        int tick = tickCounter++;

        for (TickingSystem system : systems) {
            if (!system.isEnabled()) {
                continue;
            }
            system.tick(this);
        }

        List<Request> finished = requests.tick(tick);
        reapDeadComponents();
        return finished;
    }

    @Override
    public String toString() {
        return "SystemRegistry{systems=" + systems.size()
                + ", " + requests
                + ", componentTypes=" + componentStore.size()
                + ", definitions=" + definitions.size()
                + ", tick=" + tickCounter + "}";
    }

    // ------------------------------------------------------------------
    // 自测
    // ------------------------------------------------------------------

    /** 自测用：记录调用顺序的系统。 */
    private static final class RecordingSystem implements TickingSystem {
        private final String name;
        private final int order;
        private final List<String> log;
        private final boolean enabled;

        RecordingSystem(String name, int order, List<String> log) {
            this(name, order, log, true);
        }

        RecordingSystem(String name, int order, List<String> log, boolean enabled) {
            this.name = name;
            this.order = order;
            this.log = log;
            this.enabled = enabled;
        }

        @Override
        public void tick(SystemRegistry registry) {
            log.add(name + "@" + registry.tickCounter());
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public String systemName() {
            return name;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }

    /** 自测用组件。 */
    private static final class DemoComponent implements Component {
        private final String tag;

        DemoComponent(String tag) {
            this.tag = tag;
        }

        @Override
        public String toString() {
            return "DemoComponent{" + tag + "}";
        }
    }

    /** 自测用定义。 */
    private static final class DemoDefinition implements ComponentDefinition<DemoComponent> {
        private int bindCount;
        private int initCount;

        @Override
        public void bindType() {
            bindCount++;
        }

        @Override
        public void initialize(Object context, DemoComponent component) {
            initCount++;
        }

        @Override
        public Class<DemoComponent> componentType() {
            return DemoComponent.class;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== SystemRegistry 测试 ===");

        SystemRegistry registry = new SystemRegistry();
        List<String> log = new ArrayList<>();

        // 乱序注册，验证按 order 排序
        registry.register(new RecordingSystem("Teleport", 40, log));
        registry.register(new RecordingSystem("Input", 10, log));
        registry.register(new RecordingSystem("Collision", 30, log));
        registry.register(new RecordingSystem("Move", 20, log));
        registry.register(new RecordingSystem("Disabled", 99, log, false));

        DemoDefinition def = new DemoDefinition();
        registry.registerDefinition(def);

        System.out.println("系统顺序    = " + registry.systems().stream()
                .map(TickingSystem::systemName).toList());
        System.out.println("bindType 次数 = " + def.bindCount);

        // 挂组件（应触发 initialize）
        registry.addComponent(new DemoComponent("a"), "ctx");
        registry.addComponent(new DemoComponent("b"), "ctx");
        System.out.println("initialize 次数 = " + def.initCount);
        System.out.println("组件视图    = " + registry.components(DemoComponent.class));

        // 跑 3 Tick
        registry.requests().add(new DelayRequest(2));
        for (int i = 0; i < 3; i++) {
            List<Request> done = registry.tick();
            System.out.println("tick " + i + "      = " + registry + "  完成=" + done.size());
        }

        System.out.println("调用顺序    = " + log);

        // 断言
        if (def.bindCount != 1) {
            throw new AssertionError("bindType 应只跑一次");
        }
        if (def.initCount != 2) {
            throw new AssertionError("initialize 应跑两次");
        }
        if (log.contains("Disabled@0")) {
            throw new AssertionError("禁用系统不应被调用");
        }
        // 校验首 Tick 的系统调用顺序（去掉 @tick 后缀，只比系统名）
        String firstTick = log.subList(0, 4).stream()
                .map(s -> s.substring(0, s.indexOf('@')))
                .toList()
                .toString();
        if (!firstTick.equals("[Input, Move, Collision, Teleport]")) {
            throw new AssertionError("系统顺序错误: " + firstTick);
        }
        System.out.println("首Tick顺序  = " + firstTick);
        try {
            registry.register(new RecordingSystem("Late", 1, log));
            throw new AssertionError("tick 后注册应被拒绝");
        } catch (IllegalStateException expected) {
            System.out.println("冻结保护    = PASS");
        }
        System.out.println("全部 PASS");
    }
}
