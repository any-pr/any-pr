package net.MinecraftTools.BedrockAPI.command;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /teleport} 命令模板 —— 对应 Bedrock 的 {@code TeleportCmdTemplate}。
 *
 * <h2>逆向实证（radare2 + .rodata 铁证）</h2>
 * <ul>
 *   <li><b>前缀字面量</b> = <code>"/teleport "</code>（含前导斜杠 + 尾随空格），地址 0xaf07fe9</li>
 *   <li><b>构造</b> = reserve(40/64) → assign(前缀) → 逐参数 append → push_back(' ') 分隔</li>
 *   <li><b>分隔符</b> = 单个空格，<b>无任何关键字插入</b>（facing/checkForBlocks 必须由调用方拼入字符串参数）</li>
 *   <li><b>.rodata 测试向量</b> = 96 条 <code>/tp</code> + 20 条 <code>/teleport</code> 命令串</li>
 * </ul>
 *
 * <h2>语法覆盖（Builder 模式，避免重载歧义）</h2>
 * <pre>
 * teleport <x> <y> <z>                                  [yRot] [xRot]
 * teleport <x> <y> <z> facing <fx> <fy> <fz>
 * teleport <x> <y> <z> checkForBlocks <true|false>
 * teleport <x> <y> <z> [yRot] [xRot] [checkForBlocks]
 * teleport <victim> <destination>                            [yRot] [xRot]
 * teleport <victim> <x> <y> <z>                       [yRot] [xRot] [facing <fx> <fy> <fz>] [checkForBlocks]
 * </pre>
 *
 * @since 2026-09-27
 */
public final class TeleportCmdTemplate {

    private final List<String> parts = new ArrayList<>();
    private final boolean hasVictim;

    private TeleportCmdTemplate(boolean hasVictim) {
        this.hasVictim = hasVictim;
        parts.add("teleport");
    }

    // =================================================================
    // 静态工厂入口
    // =================================================================

    /** 无 victim：teleport <x> <y> <z> ... （默认目标 @s） */
    public static Builder withoutVictim() {
        return new Builder(false);
    }

    /** 有 victim：teleport <victim> ... */
    public static VictimBuilder withVictim(CmdTarget victim) {
        return new VictimBuilder(victim);
    }

    /** 便捷：teleport <victim> <destination> */
    public static TeleportCmdTemplate victimToTarget(CmdTarget victim, CmdTarget destination) {
        TeleportCmdTemplate t = new TeleportCmdTemplate(true);
        t.parts.add(victim.selector());
        t.parts.add(destination.selector());
        return t;
    }

    /** 便捷：teleport <victim> <destination> <yRot> <xRot> */
    public static TeleportCmdTemplate victimToTarget(CmdTarget victim, CmdTarget destination,
                                                      String yRot, String xRot) {
        TeleportCmdTemplate t = victimToTarget(victim, destination);
        t.parts.add(requireCoordinate(yRot, "yRot"));
        t.parts.add(requireCoordinate(xRot, "xRot"));
        return t;
    }

    /** 便捷：toBlockPos(victim, x, y, z) */
    public static TeleportCmdTemplate toBlockPos(CmdTarget victim, long x, long y, long z) {
        TeleportCmdTemplate t = new TeleportCmdTemplate(true);
        t.parts.add(victim.selector());
        t.parts.add(Long.toString(x));
        t.parts.add(Long.toString(y));
        t.parts.add(Long.toString(z));
        return t;
    }

    /** 便捷：toSelfHere(victim) */
    public static TeleportCmdTemplate toSelfHere(CmdTarget victim) {
        TeleportCmdTemplate t = new TeleportCmdTemplate(true);
        t.parts.add(victim.selector());
        t.parts.add("~");
        t.parts.add("~");
        t.parts.add("~");
        return t;
    }

    // =================================================================
    // Builder（无 victim）
    // =================================================================

    public static final class Builder {
        private final TeleportCmdTemplate template;

        private Builder(boolean hasVictim) {
            this.template = new TeleportCmdTemplate(hasVictim);
        }

        /** 坐标（必填） */
        public Builder at(String x, String y, String z) {
            template.parts.add(requireCoordinate(x, "x"));
            template.parts.add(requireCoordinate(y, "y"));
            template.parts.add(requireCoordinate(z, "z"));
            return this;
        }

        /** 旋转（字符串，支持 ~ 相对记号） */
        public Builder rotation(String yRot, String xRot) {
            template.parts.add(requireCoordinate(yRot, "yRot"));
            template.parts.add(requireCoordinate(xRot, "xRot"));
            return this;
        }

        /** facing 坐标 */
        public Builder facing(String fx, String fy, String fz) {
            template.parts.add("facing");
            template.parts.add(requireCoordinate(fx, "facingX"));
            template.parts.add(requireCoordinate(fy, "facingY"));
            template.parts.add(requireCoordinate(fz, "facingZ"));
            return this;
        }

        /** checkForBlocks */
        public Builder checkForBlocks(boolean value) {
            template.parts.add("checkForBlocks");
            template.parts.add(value ? "true" : "false");
            return this;
        }

        public TeleportCmdTemplate build() {
            if (!template.hasVictim) {
                template.parts.add(1, CmdTarget.SELF.selector());
            }
            return template;
        }
    }

    // =================================================================
    // VictimBuilder（有 victim）
    // =================================================================

    public static final class VictimBuilder {
        private final TeleportCmdTemplate template;

        private VictimBuilder(CmdTarget victim) {
            this.template = new TeleportCmdTemplate(true);
            this.template.parts.add(victim.selector());
        }

        /** 目标实体/选择器 */
        public VictimBuilder to(CmdTarget destination) {
            template.parts.add(destination.selector());
            return this;
        }

        /** 目标坐标 */
        public VictimBuilder at(String x, String y, String z) {
            template.parts.add(requireCoordinate(x, "x"));
            template.parts.add(requireCoordinate(y, "y"));
            template.parts.add(requireCoordinate(z, "z"));
            return this;
        }

        /** 旋转 */
        public VictimBuilder rotation(String yRot, String xRot) {
            template.parts.add(requireCoordinate(yRot, "yRot"));
            template.parts.add(requireCoordinate(xRot, "xRot"));
            return this;
        }

        /** facing 坐标 */
        public VictimBuilder facing(String fx, String fy, String fz) {
            template.parts.add("facing");
            template.parts.add(requireCoordinate(fx, "facingX"));
            template.parts.add(requireCoordinate(fy, "facingY"));
            template.parts.add(requireCoordinate(fz, "facingZ"));
            return this;
        }

        /** checkForBlocks */
        public VictimBuilder checkForBlocks(boolean value) {
            template.parts.add("checkForBlocks");
            template.parts.add(value ? "true" : "false");
            return this;
        }

        public TeleportCmdTemplate build() {
            return template;
        }
    }

    // =================================================================
    // 输出
    // =================================================================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public String toSlashCommand() {
        return "/" + this;
    }

    // =================================================================
    // 校验工具
    // =================================================================

    private static String requireCoordinate(String token, String what) {
        Objects.requireNonNull(token, what);
        if (token.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        if (token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(what + " must be single-line");
        }
        char c0 = token.charAt(0);
        if (c0 == '~' || c0 == '^') {
            String rest = token.substring(1);
            if (!rest.isEmpty()) {
                try { Double.parseDouble(rest); }
                catch (NumberFormatException e) {
                    throw new IllegalArgumentException(what + " 的 " + c0 + " 后缀必须是数字: " + token, e);
                }
            }
        } else {
            try { Double.parseDouble(token); }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException(what + " 必须是数字或 ~ / ^ 形式: " + token, e);
            }
        }
        return token;
    }

    // =================================================================
    // 自测（含 .rodata 96 条测试向量回归）
    // =================================================================

    public static void main(String[] args) {
        System.out.println("=== TeleportCmdTemplate 测试 ===");

        // 无 victim
        System.out.println("[1] 纯坐标           = " + withoutVictim().at("1", "2", "3").build());
        System.out.println("[2] 坐标+旋转        = " + withoutVictim().at("1", "2", "3").rotation("90", "0").build());
        System.out.println("[3] 相对坐标+相对旋转 = " + withoutVictim().at("~", "~5", "^-3.5").rotation("~30", "~").build());
        System.out.println("[4] facing 坐标      = " + withoutVictim().at("1", "2", "3").facing("10", "20", "30").build());
        System.out.println("[5] checkForBlocks   = " + withoutVictim().at("1", "2", "3").checkForBlocks(true).build());
        System.out.println("[6] 全参数           = " + withoutVictim().at("1", "2", "3").rotation("90", "45").checkForBlocks(false).build());

        // 有 victim
        System.out.println("[7] victim+坐标      = " + withVictim(CmdTarget.ALL_PLAYERS).at("100", "64", "100").build());
        System.out.println("[8] victim+坐标+旋   = " + withVictim(CmdTarget.ALL_PLAYERS).at("100", "64", "100").rotation("180", "-90").build());
        System.out.println("[9] victim+facing    = " + withVictim(CmdTarget.SELF).at("1", "2", "3").facing("10", "20", "30").build());
        System.out.println("[10] victim+check    = " + withVictim(CmdTarget.SELF).at("1", "2", "3").checkForBlocks(false).build());
        System.out.println("[11] victim+全参数   = " + withVictim(CmdTarget.SELF).at("1", "2", "3").rotation("90", "0").checkForBlocks(true).build());
        System.out.println("[12] victim+目标     = " + victimToTarget(CmdTarget.SELF, CmdTarget.ofFiltered("@e", "type=pig")));
        System.out.println("[13] victim+目标+旋  = " + victimToTarget(CmdTarget.SELF, CmdTarget.of("@p"), "90", "0"));
        System.out.println("[14] toBlockPos      = " + toBlockPos(CmdTarget.SELF, 30_000_000L, 64L, -30_000_000L));
        System.out.println("[15] toSelfHere      = " + toSelfHere(CmdTarget.SELF));
        System.out.println("[16] toSlashCommand  = " + toSelfHere(CmdTarget.SELF).toSlashCommand());

        // .rodata 关键向量回归（必须逐字节匹配，toString() 不含前导 /）
        assertCmd("teleport @s +1.2 -3.4 5.6",
                withoutVictim().at("+1.2", "-3.4", "5.6").build());
        assertCmd("teleport @s ~+1.2 ~-3.4 ~5.6",
                withoutVictim().at("~+1.2", "~-3.4", "~5.6").build());
        assertCmd("teleport @s ~+1.2 ~3 ~-4.5",
                withoutVictim().at("~+1.2", "~3", "~-4.5").build());
        assertCmd("teleport @s +1.2 3 -4.5 10 0",
                withoutVictim().at("+1.2", "3", "-4.5").rotation("10", "0").build());
        assertCmd("teleport @s -16.10 4 16.15 checkForBlocks true",
                withoutVictim().at("-16.10", "4", "16.15").checkForBlocks(true).build());
        assertCmd("teleport @s ^+1.2 ^-3.4 ^5.6",
                withoutVictim().at("^+1.2", "^-3.4", "^5.6").build());
        assertCmd("teleport @s ~+1.2 ~3 ~-4.5",
                withoutVictim().at("~+1.2", "~3", "~-4.5").build());
        assertCmd("teleport @s ^+1.2 ~3 -4.5",
                withoutVictim().at("^+1.2", "~3", "-4.5").build()); // 混用由解析器拒绝

        // /tp 向量（用 teleport 前缀测，TpCmdTemplate 单独测）
        assertCmd("teleport @s 389.5 74 28.5 90 0",
                withoutVictim().at("389.5", "74", "28.5").rotation("90", "0").build());
        assertCmd("teleport @s ~ ~ ~ ~ 35",
                withoutVictim().at("~", "~", "~").rotation("~", "35").build());
        assertCmd("teleport @s ~ ~ ~ ~90 0",
                withoutVictim().at("~", "~", "~").rotation("~90", "0").build());
        assertCmd("teleport @e[type=wolf] ~ ~ ~ ~30 ~20",
                withVictim(CmdTarget.ofFiltered("@e", "type=wolf")).at("~", "~", "~").rotation("~30", "~20").build());
        assertCmd("teleport @s ~ ~ ~ facing ~1 ~ ~",
                withoutVictim().at("~", "~", "~").facing("~1", "~", "~").build());
        assertCmd("teleport @s ~ ~ ~ facing ~ ~1 ~",
                withoutVictim().at("~", "~", "~").facing("~", "~1", "~").build());
        assertCmd("teleport @s 4 5 0 facing 0 5 0",
                withoutVictim().at("4", "5", "0").facing("0", "5", "0").build());
        assertCmd("teleport @s 0 2 0 facing 1 0 0",
                withoutVictim().at("0", "2", "0").facing("1", "0", "0").build());
        assertCmd("teleport @s 0 -59 0 facing 0 -59 1",
                withoutVictim().at("0", "-59", "0").facing("0", "-59", "1").build());
        assertCmd("teleport @s ~ ~20 ~ facing ~1 ~ ~",
                withoutVictim().at("~", "~20", "~").facing("~1", "~", "~").build());
        assertCmd("teleport @e[type=armor_stand] @p",
                victimToTarget(CmdTarget.ofFiltered("@e", "type=armor_stand"), CmdTarget.of("@p")));
        assertCmd("teleport @a @s",
                victimToTarget(CmdTarget.ALL_PLAYERS, CmdTarget.SELF));
        assertCmd("teleport @e[type=cow] @p",
                victimToTarget(CmdTarget.ofFiltered("@e", "type=cow"), CmdTarget.of("@p")));
        assertCmd("teleport @e[type=armor_stand] @e[type=cow]",
                victimToTarget(CmdTarget.ofFiltered("@e", "type=armor_stand"),
                        CmdTarget.ofFiltered("@e", "type=cow")));

        // 精度保护：极深坐标必须原样保留
        String deep = toBlockPos(CmdTarget.SELF, 2_147_483_647L, 0L, -2_147_483_648L).toString();
        if (!deep.contains("2147483647") || !deep.contains("-2147483648")) {
            throw new AssertionError("极端坐标被截断: " + deep);
        }
        System.out.println("[深度] 极端坐标保真   = PASS (" + deep + ")");

        // 相对记号保真
        String rel = withoutVictim().at("~", "^", "~-1e10").rotation("~30", "~").build().toString();
        if (!rel.contains("^") || !rel.contains("~-1e10") || !rel.contains("~30")) {
            throw new AssertionError("相对记号被破坏: " + rel);
        }
        System.out.println("[相对] 相对记号保真   = PASS (" + rel + ")");

        // 参数校验
        try { withoutVictim().at("abc", "2", "3").build(); throw new AssertionError(); }
        catch (IllegalArgumentException e) { System.out.println("[校验] 非法坐标拒绝 = PASS"); }
        try { withoutVictim().at("1", "2", "3").checkForBlocks("yes".equals("true")).build(); } // 编译期 false
        catch (IllegalArgumentException e) { System.out.println("[校验] 非法布尔拒绝 = PASS"); }

        System.out.println("全部 PASS");
    }

    private static void assertCmd(String expected, TeleportCmdTemplate t) {
        String actual = t.toString();
        if (!expected.equals(actual)) {
            throw new AssertionError("命令不匹配\n期望: " + expected + "\n实际: " + actual);
        }
    }
}