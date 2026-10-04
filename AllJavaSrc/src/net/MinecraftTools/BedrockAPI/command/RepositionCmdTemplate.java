package net.MinecraftTools.BedrockAPI.command;

/**
 * 世界重定位命令模板 —— MCRe 专有（Bedrock 无对应类，但沿用 {@link CmdTemplate} 规范）。
 *
 * <p>对应 MCRe 的 WorldReposition（偏移 + 缩放）功能。坐标一律以文本保存，
 * 因为重定位参数可能是 256-bit / DynamicNumber 的<b>超长十进制字面量</b>，
 * 转成 {@code double} 会丢精度。
 *
 * @since 2026-09-27
 */
public final class RepositionCmdTemplate extends CmdTemplate {

    /** {@code reposition offset <dx> <dy> <dz>} —— 平移。 */
    public RepositionCmdTemplate(String dx, String dy, String dz) {
        super("reposition");
        appendKeyword("offset");
        append(requireBigNumber(dx, "dx"));
        append(requireBigNumber(dy, "dy"));
        append(requireBigNumber(dz, "dz"));
    }

    /** 私有：避免与三参平移重载歧义。 */
    private RepositionCmdTemplate(String cmd, boolean scale) {
        super(cmd);
        if (!scale) {
            throw new IllegalArgumentException("internal");
        }
    }

    /** {@code reposition scale <factor>} —— 缩放。 */
    public static RepositionCmdTemplate scale(String factor) {
        RepositionCmdTemplate t = new RepositionCmdTemplate("reposition", true);
        t.appendKeyword("scale");
        t.append(requireBigNumber(factor, "factor"));
        return t;
    }

    /** {@code reposition reset} —— 复位。 */
    public static RepositionCmdTemplate reset() {
        RepositionCmdTemplate t = new RepositionCmdTemplate("reposition", true);
        t.appendKeyword("reset");
        return t;
    }

    /** {@code reposition query} —— 查询当前偏移。 */
    public static RepositionCmdTemplate query() {
        RepositionCmdTemplate t = new RepositionCmdTemplate("reposition", true);
        t.appendKeyword("query");
        return t;
    }

    /**
     * 校验一个可能超长的十进制数字字面量。
     *
     * <p>不转 {@code double}，只做字符集与格式校验，保证 256-bit 精度不丢。
     */
    private static String requireBigNumber(String token, String what) {
        requireToken(token, what);
        int i = 0;
        if (token.charAt(0) == '-' || token.charAt(0) == '+') {
            i = 1;
        }
        boolean dotSeen = false;
        boolean digitSeen = false;
        boolean expSeen = false;
        for (; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c >= '0' && c <= '9') {
                digitSeen = true;
            } else if (c == '.' && !dotSeen && !expSeen) {
                dotSeen = true;
            } else if ((c == 'e' || c == 'E') && digitSeen && !expSeen) {
                expSeen = true;
                if (i + 1 < token.length()
                        && (token.charAt(i + 1) == '-' || token.charAt(i + 1) == '+')) {
                    i++;
                }
            } else {
                throw new IllegalArgumentException(what + " 不是合法数字字面量: " + token);
            }
        }
        if (!digitSeen) {
            throw new IllegalArgumentException(what + " 缺少数字: " + token);
        }
        return token;
    }

    public static void main(String[] args) {
        System.out.println("=== RepositionCmdTemplate 测试 ===");
        System.out.println("平移        = " + new RepositionCmdTemplate("100", "-200", "0"));
        System.out.println("缩放        = " + scale("1.5"));
        System.out.println("复位        = " + reset());
        System.out.println("查询        = " + query());

        // 超长精度保护
        String huge = "1" + "0".repeat(80);
        String cmd = new RepositionCmdTemplate(huge, "0", "0").toString();
        if (!cmd.contains(huge)) {
            throw new AssertionError("超长数字被破坏: " + cmd);
        }
        System.out.println("80 位精度保真 = PASS");

        String negExp = "-1.25e-40";
        System.out.println("科学计数法   = " + scale(negExp));

        try {
            new RepositionCmdTemplate("1.2.3", "0", "0");
            throw new AssertionError("非法数字应被拒绝");
        } catch (IllegalArgumentException expected) {
            System.out.println("非法数字拒绝  = PASS");
        }
        System.out.println("全部 PASS");
    }
}
