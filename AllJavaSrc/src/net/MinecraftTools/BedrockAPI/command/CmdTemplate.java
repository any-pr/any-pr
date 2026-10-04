package net.MinecraftTools.BedrockAPI.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 命令模板基类 —— 对应 Bedrock 的 {@code CmdTemplate}（含 {@code operator std::string()}）。
 *
 * <h2>核心设计（照搬 Bedrock）</h2>
 * <ol>
 *   <li><b>参数以文本保存，不预先解析。</b>{@code ~}(相对) / {@code ^}(局部) / 维度后缀
 *       必须原样透传，一旦先转成数值再拼回去，相对坐标的语义就丢了。</li>
 *   <li><b>延迟拼装。</b>构造只存字段，{@link #toString()} 才生成命令文本。</li>
 *   <li><b>禁止字符串拼接。</b>调用方一律用子类构造函数，不手写 {@code "/tp " + x + ...}。</li>
 * </ol>
 *
 * <h2>Bedrock 佐证</h2>
 * {@code TeleportCmdTemplate} 在符号表里有 6 个构造函数，字节码 Size 随参数个数
 * 精确递增（每多一个 {@code std::string} 参数稳定 +36 字节），说明就是「逐参数格式化」。
 * 参数类型是 {@code std::string} 而非 {@code double} —— 这正是「原样透传」的铁证。
 *
 * <h2>用法</h2>
 * <pre>{@code
 * // ✅ 正确：用模板
 * String cmd = new TeleportCmdTemplate(CmdTarget.SELF, "~", "~", "~", 90f, 0f).toString();
 * //   → "tp @s ~ ~ ~ 90 0"
 *
 * // ❌ 禁止：手拼字符串（无法保证 ~ ^ 维度后缀不被破坏）
 * String bad = "/tp " + target + " " + x + " " + y + " " + z;
 * }</pre>
 *
 * @since 2026-09-27
 */
public abstract class CmdTemplate {

    /** 命令名（不含前导 {@code /}），例如 {@code tp} / {@code reposition}。 */
    private final String commandName;

    /** 参数片段（按加入顺序拼装）。 */
    private final List<String> parts = new ArrayList<>();

    protected CmdTemplate(String commandName) {
        this.commandName = requireToken(commandName, "commandName");
    }

    /** 命令名（不含前导 {@code /}）。 */
    public final String commandName() {
        return commandName;
    }

    /**
     * 追加一个位置参数。
     *
     * @param token 参数文本；{@code null} 或空白将抛异常（防止悄悄丢参数）
     * @return {@code this}，便于链式调用
     */
    protected final CmdTemplate append(String token) {
        parts.add(requireToken(token, "token"));
        return this;
    }

    /** 追加一个浮点参数（Bedrock 的 {@code float} 重载对应此路径）。 */
    protected final CmdTemplate append(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("float param must be finite: " + value);
        }
        // 整数值去掉 ".0" 尾巴，与 Bedrock 模板的格式化行为一致
        if (value == Math.rint(value) && Math.abs(value) < 1.0e7f) {
            parts.add(Integer.toString((int) value));
        } else {
            parts.add(Float.toString(value));
        }
        return this;
    }

    /** 追加一个已校验的目标选择器。 */
    protected final CmdTemplate append(CmdTarget target) {
        parts.add(Objects.requireNonNull(target, "target").selector());
        return this;
    }

    /** 追加一个关键字（如 {@code facing}）。 */
    protected final CmdTemplate appendKeyword(String keyword) {
        return append(keyword);
    }

    /** 当前参数个数。 */
    public final int arity() {
        return parts.size();
    }

    /** 只读视图，供子类/测试检查。 */
    protected final List<String> parts() {
        return List.copyOf(parts);
    }

    /** 拼装成完整命令文本（<b>不含</b>前导 {@code /}，交由调用方按需加）。 */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(commandName);
        for (String part : parts) {
            sb.append(' ').append(part);
        }
        return sb.toString();
    }

    /** 拼装成带前导 {@code /} 的完整命令文本。 */
    public final String toSlashCommand() {
        return "/" + this;
    }

    // ------------------------------------------------------------------
    // 校验工具
    // ------------------------------------------------------------------

    /** 校验一个参数 token：非 null、非空白、单行。 */
    protected static String requireToken(String token, String what) {
        Objects.requireNonNull(token, what);
        if (token.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        if (token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(what + " must be single-line: " + token);
        }
        return token;
    }

    /** 校验一个坐标分量：允许 {@code ~} / {@code ^} / {@code ~5} / {@code ^-3.5} / 纯数字。 */
    protected static String requireCoordinate(String token, String axis) {
        requireToken(token, axis);
        char c0 = token.charAt(0);
        if (c0 == '~' || c0 == '^') {
            String rest = token.substring(1);
            if (rest.isEmpty()) {
                return token; // "~" / "^" 合法
            }
            try {
                Double.parseDouble(rest);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        axis + " 的 " + c0 + " 后缀必须是数字: " + token, e);
            }
            return token;
        }
        try {
            Double.parseDouble(token);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(axis + " 必须是数字或 ~ / ^ 形式: " + token, e);
        }
        return token;
    }

    public static void main(String[] args) {
        System.out.println("=== CmdTemplate 测试 ===");

        // 用一个最小匿名子类验证基类行为
        CmdTemplate t = new CmdTemplate("demo") { };
        t.append("~").append("~5").append("^-3.5");
        t.append(90f).append(0f).append(12.5f);
        t.append(CmdTarget.SELF);
        System.out.println("arity         = " + t.arity());
        System.out.println("toString      = " + t);
        System.out.println("toSlashCommand= " + t.toSlashCommand());

        // 坐标校验
        System.out.println("requireCoordinate(~)     = " + requireCoordinate("~", "x"));
        System.out.println("requireCoordinate(^-3.5) = " + requireCoordinate("^-3.5", "x"));
        System.out.println("requireCoordinate(1.5)   = " + requireCoordinate("1.5", "x"));
        try {
            requireCoordinate("~abc", "x");
            throw new AssertionError("~abc 应被拒绝");
        } catch (IllegalArgumentException expected) {
            System.out.println("拒绝 ~abc     = PASS");
        }
        try {
            requireCoordinate("xyz", "x");
            throw new AssertionError("xyz 应被拒绝");
        } catch (IllegalArgumentException expected) {
            System.out.println("拒绝 xyz      = PASS");
        }
        try {
            t.append((String) null);
            throw new AssertionError("null 参数应被拒绝");
        } catch (NullPointerException expected) {
            System.out.println("拒绝 null     = PASS");
        }
        System.out.println("全部 PASS");
    }
}
