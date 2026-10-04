package net.MinecraftTools.BedrockAPI.toggle;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 编译期特性开关 —— 对应 Bedrock 的 {@code FeatureToggles}。
 *
 * <h2>用法（重要）</h2>
 * <pre>{@code
 * // ✅ 热路径：编译期常量，javac 常量折叠，死分支被彻底消除
 * if (FeatureToggles.ENABLE_MULTIDRAW_INDIRECT) {
 *     renderIndirect();      // ENABLE_... = false 时，这段字节码根本不存在
 * } else {
 *     renderSeparate();
 * }
 *
 * // ✅ 冷路径 / 初始化 / 调试：支持运行期覆盖
 * if (FeatureToggles.isEnabled(Feature.MULTIDRAW_INDIRECT)) { ... }
 * }</pre>
 *
 * <h2>常量维护规则</h2>
 * {@code ENABLE_*} 字面量必须与 {@link Feature#defaultValue()} 保持一致，
 * 由 {@link #verifyConsistency()} 在 {@link #main} 自测中校验。
 * 发布构建时由 Gradle 按 profile 重写字面量（对应 Bedrock 在 CMake 阶段剔除代码）。
 *
 * @since 2026-09-27
 */
public final class FeatureToggles {

    private FeatureToggles() {
        throw new AssertionError("no instance");
    }

    // =====================================================================
    // 编译期常量轨 —— Gradle 按 profile 重写这里的字面量
    // 必须与 Feature.<X>.defaultValue() 一致，由 verifyConsistency() 校验
    // =====================================================================

    // 坐标与精度
    public static final boolean ENABLE_LONG_COORDINATE = true;
    public static final boolean ENABLE_WINDOWED_CHUNK = true;
    public static final boolean ENABLE_WINDOW_FOLLOW_CAMERA = false;
    public static final boolean ENABLE_GENERATION_HEIGHT_CLAMP = true;

    // 光照
    public static final boolean ENABLE_LIGHT_ABSOLUTE_SECTION_Y = true;
    public static final boolean ENABLE_FAR_LANDS_LIGHT_CUTOFF = true;

    // 渲染
    public static final boolean ENABLE_MULTIDRAW_INDIRECT = false;
    public static final boolean ENABLE_GPU_CAMERA_RELATIVE = false;
    public static final boolean ENABLE_ENTITY_OCCLUSION_CULLING = true;
    public static final boolean ENABLE_LEAVES_INTERNAL_FACE_CULLING = true;

    // 世界生成
    public static final boolean ENABLE_WORLD_REPOSITION = false;
    public static final boolean ENABLE_Y_CLAMPED_GRADIENT_OFFSET = false;

    // 存储与网络
    public static final boolean ENABLE_CHUNK_IO_BATCH = true;
    public static final boolean ENABLE_CHUNK_PACKET_WINDOW_FILTER = true;

    // 命令与调试
    public static final boolean ENABLE_CMD_TEMPLATE = true;
    public static final boolean ENABLE_ASYNC_TELEPORT_REQUEST = false;
    public static final boolean ENABLE_CHAT_EVERYWHERE = true;

    // =====================================================================
    // 运行期覆盖轨 —— 仅开发/测试使用；发布构建里 OVERRIDES 恒为空
    // =====================================================================

    private static final Map<Feature, Boolean> OVERRIDES = new HashMap<>();

    /** 运行期查询：先看覆盖表，再落回编译期默认值。 */
    public static boolean isEnabled(Feature feature) {
        Objects.requireNonNull(feature, "feature");
        Boolean override = OVERRIDES.get(feature);
        return override != null ? override : feature.defaultValue();
    }

    /** 临时覆盖（仅开发/测试）。发布构建调用此方法应被 Gradle 剔除或忽略。 */
    public static void override(Feature feature, boolean value) {
        Objects.requireNonNull(feature, "feature");
        OVERRIDES.put(feature, value);
    }

    /** 清除全部覆盖。 */
    public static void clearOverrides() {
        OVERRIDES.clear();
    }

    /** 当前是否存在覆盖项（发布构建应为 {@code false}）。 */
    public static boolean hasOverrides() {
        return !OVERRIDES.isEmpty();
    }

    /**
     * 校验编译期常量与 {@link Feature#defaultValue()} 一致。
     *
     * @return 不一致的特性列表；空列表表示全部一致
     */
    public static java.util.List<Feature> verifyConsistency() {
        java.util.List<Feature> mismatched = new java.util.ArrayList<>();
        for (Feature feature : Feature.values()) {
            if (constantOf(feature) != feature.defaultValue()) {
                mismatched.add(feature);
            }
        }
        return mismatched;
    }

    /**
     * 取某特性的编译期常量值。
     *
     * <p>注意：本方法用 {@code switch} 返回常量，**不**享有常量折叠收益，
     * 只用于自测与反射场景；热路径请直接引用 {@code ENABLE_*} 字段。
     */
    public static boolean constantOf(Feature feature) {
        return switch (Objects.requireNonNull(feature, "feature")) {
            case LONG_COORDINATE -> ENABLE_LONG_COORDINATE;
            case WINDOWED_CHUNK -> ENABLE_WINDOWED_CHUNK;
            case WINDOW_FOLLOW_CAMERA -> ENABLE_WINDOW_FOLLOW_CAMERA;
            case GENERATION_HEIGHT_CLAMP -> ENABLE_GENERATION_HEIGHT_CLAMP;
            case LIGHT_ABSOLUTE_SECTION_Y -> ENABLE_LIGHT_ABSOLUTE_SECTION_Y;
            case FAR_LANDS_LIGHT_CUTOFF -> ENABLE_FAR_LANDS_LIGHT_CUTOFF;
            case MULTIDRAW_INDIRECT -> ENABLE_MULTIDRAW_INDIRECT;
            case GPU_CAMERA_RELATIVE -> ENABLE_GPU_CAMERA_RELATIVE;
            case ENTITY_OCCLUSION_CULLING -> ENABLE_ENTITY_OCCLUSION_CULLING;
            case LEAVES_INTERNAL_FACE_CULLING -> ENABLE_LEAVES_INTERNAL_FACE_CULLING;
            case WORLD_REPOSITION -> ENABLE_WORLD_REPOSITION;
            case Y_CLAMPED_GRADIENT_OFFSET -> ENABLE_Y_CLAMPED_GRADIENT_OFFSET;
            case CHUNK_IO_BATCH -> ENABLE_CHUNK_IO_BATCH;
            case CHUNK_PACKET_WINDOW_FILTER -> ENABLE_CHUNK_PACKET_WINDOW_FILTER;
            case CMD_TEMPLATE -> ENABLE_CMD_TEMPLATE;
            case ASYNC_TELEPORT_REQUEST -> ENABLE_ASYNC_TELEPORT_REQUEST;
            case CHAT_EVERYWHERE -> ENABLE_CHAT_EVERYWHERE;
        };
    }

    /** 启用计数（编译期常量统计，便于自测打印）。 */
    public static int enabledCount() {
        int n = 0;
        for (Feature feature : Feature.values()) {
            if (constantOf(feature)) {
                n++;
            }
        }
        return n;
    }

    public static void main(String[] args) {
        System.out.println("=== FeatureToggles 测试 ===");

        java.util.List<Feature> mismatched = verifyConsistency();
        System.out.println("特性总数        = " + Feature.values().length);
        System.out.println("编译期启用数    = " + enabledCount());
        System.out.println("常量一致性      = " + (mismatched.isEmpty() ? "PASS" : "FAIL " + mismatched));

        // 覆盖轨
        System.out.println("MULTIDRAW 默认  = " + isEnabled(Feature.MULTIDRAW_INDIRECT));
        override(Feature.MULTIDRAW_INDIRECT, true);
        System.out.println("MULTIDRAW 覆盖后= " + isEnabled(Feature.MULTIDRAW_INDIRECT));
        clearOverrides();
        System.out.println("清除后          = " + isEnabled(Feature.MULTIDRAW_INDIRECT));

        // 编译期常量直接引用（javac 会折叠成常量，不产生方法调用）
        if (ENABLE_LONG_COORDINATE) {
            System.out.println("LONG_COORDINATE = 已启用（该分支被编译期保留）");
        }
        if (ENABLE_MULTIDRAW_INDIRECT) {
            System.out.println("MULTIDRAW       = 已启用");
        } else {
            System.out.println("MULTIDRAW       = 已禁用（该分支被编译期消除）");
        }

        if (!mismatched.isEmpty()) {
            throw new AssertionError("FeatureToggles 常量与 Feature.defaultValue() 不一致: " + mismatched);
        }
        System.out.println("全部 PASS");
    }
}
