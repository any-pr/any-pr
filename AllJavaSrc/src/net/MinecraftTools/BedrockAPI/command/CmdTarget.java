package net.MinecraftTools.BedrockAPI.command;

import java.util.Objects;

/**
 * 命令目标选择器 —— 对应 Bedrock 的 {@code CmdTarget}。
 *
 * <p>Bedrock 的 {@code CmdTarget} 是一个<b>已校验</b>的选择器包装类型，
 * 由 {@code TemplateUtil::toString(CmdTarget const&)} 渲染成命令文本。
 * 本类保持同样语义：构造时做基础校验，{@link #toString()} 直接输出原文。
 *
 * <p><b>为什么不用 String 直接传？</b>因为 {@code CmdTarget} 把「这是一个目标」
 * 与「这是一段坐标/维度文本」在类型层面区分开，避免 {@link CmdTemplate} 的重载歧义
 * （Bedrock 的 6 个 {@code TeleportCmdTemplate} 构造函数正是靠这个区分）。
 *
 * @since 2026-09-27
 */
public record CmdTarget(String selector) {

    /** {@code @s} —— 执行者自身。 */
    public static final CmdTarget SELF = new CmdTarget("@s");

    /** {@code @a} —— 所有玩家。 */
    public static final CmdTarget ALL_PLAYERS = new CmdTarget("@a");

    /** {@code @e} —— 所有实体。 */
    public static final CmdTarget ALL_ENTITIES = new CmdTarget("@e");

    /** {@code @p} —— 最近玩家。 */
    public static final CmdTarget NEAREST_PLAYER = new CmdTarget("@p");

    /** {@code @r} —— 随机玩家。 */
    public static final CmdTarget RANDOM_PLAYER = new CmdTarget("@r");

    /** {@code @i} —— 所有实体（Bedrock 专有，含非存活实体）。 */
    public static final CmdTarget ALL_ENTITIES_INCLUDING_DEAD = new CmdTarget("@i");

    /** {@code @c} —— 执行者的相机目标（Bedrock 专有）。 */
    public static final CmdTarget CAMERA = new CmdTarget("@c");

    public CmdTarget {
        Objects.requireNonNull(selector, "selector");
        if (selector.isBlank()) {
            throw new IllegalArgumentException("CmdTarget selector must not be blank");
        }
        if (selector.indexOf('\n') >= 0 || selector.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("CmdTarget selector must be single-line: " + selector);
        }
    }

    /** 包一层原始选择器文本（不校验语法，只校验非空/单行）。 */
    public static CmdTarget of(String raw) {
        return new CmdTarget(raw);
    }

    /** 带过滤条件的选择器，例如 {@code ofFiltered("@e", "type=pig")} → {@code @e[type=pig]}。 */
    public static CmdTarget ofFiltered(String base, String filter) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(filter, "filter");
        if (filter.isBlank()) {
            return new CmdTarget(base);
        }
        return new CmdTarget(base + "[" + filter + "]");
    }

    /** 是否为通配选择器（以 {@code @} 开头）。 */
    public boolean isSelector() {
        return selector.charAt(0) == '@';
    }

    /** 是否为字面玩家名/实体名。 */
    public boolean isLiteralName() {
        return !isSelector();
    }

    @Override
    public String toString() {
        return selector;
    }

    public static void main(String[] args) {
        System.out.println("=== CmdTarget 测试 ===");
        System.out.println("SELF          = " + SELF);
        System.out.println("ALL_PLAYERS   = " + ALL_PLAYERS);
        System.out.println("CAMERA        = " + CAMERA);
        System.out.println("ofFiltered    = " + CmdTarget.ofFiltered("@e", "type=pig"));
        System.out.println("ofFiltered(空)= " + CmdTarget.ofFiltered("@e", ""));
        System.out.println("isSelector(@s)= " + SELF.isSelector());
        System.out.println("isSelector(熊) = " + CmdTarget.of("Steve").isSelector());

        try {
            CmdTarget.of("  ");
            throw new AssertionError("空白选择器应当抛异常");
        } catch (IllegalArgumentException expected) {
            System.out.println("空白拒绝      = PASS");
        }
        try {
            CmdTarget.of("a\nb");
            throw new AssertionError("多行选择器应当抛异常");
        } catch (IllegalArgumentException expected) {
            System.out.println("多行拒绝      = PASS");
        }
        System.out.println("全部 PASS");
    }
}
