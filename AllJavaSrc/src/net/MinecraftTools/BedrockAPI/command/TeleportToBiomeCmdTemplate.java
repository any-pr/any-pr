package net.MinecraftTools.BedrockAPI.command;

import java.util.Objects;

/**
 * {@code /tptobiome} 命令模板 —— Bedrock 独立命令（对应
 * <code>src/common/server/commands/test/TeleportToBiomeCommand.cpp</code>）。
 *
 * <h2>逆向实证</h2>
 * <ul>
 *   <li>.rodata 里出现 <code>/tptobiome @s deep_warm_ocean</code></li>
 *   <li>符号表有 <code>TeleportToBiomeCommand</code> + <code>TeleportToBiomeCommandServerTests</code></li>
 *   <li>语法：<code>tptobiome <target> <biome_id></code></li>
 * </ul>
 *
 * @since 2026-09-27
 */
public final class TeleportToBiomeCmdTemplate extends CmdTemplate {

    /** {@code tptobiome <target> <biome_id>}。 */
    public TeleportToBiomeCmdTemplate(CmdTarget target, String biomeId) {
        super("tptobiome");
        append(target);
        append(requireBiomeId(biomeId));
    }

    /** {@code tptobiome <biome_id>} —— 默认 @s。 */
    public TeleportToBiomeCmdTemplate(String biomeId) {
        this(CmdTarget.SELF, biomeId);
    }

    private static String requireBiomeId(String token) {
        Objects.requireNonNull(token, "biomeId");
        if (token.isBlank()) {
            throw new IllegalArgumentException("biomeId must not be blank");
        }
        if (token.indexOf(' ') >= 0 || token.indexOf('\t') >= 0) {
            throw new IllegalArgumentException("biomeId must not contain whitespace: " + token);
        }
        return token;
    }

    public static void main(String[] args) {
        System.out.println("=== TeleportToBiomeCmdTemplate 测试 ===");
        System.out.println("[1] 默认 @s = " + new TeleportToBiomeCmdTemplate("deep_warm_ocean"));
        System.out.println("[2] 指定目标 = " + new TeleportToBiomeCmdTemplate(CmdTarget.ALL_PLAYERS, "minecraft:the_end"));
        System.out.println("[3] 斜杠命令 = " + new TeleportToBiomeCmdTemplate("minecraft:plains").toSlashCommand());

        assertCmd("tptobiome @s deep_warm_ocean",
                new TeleportToBiomeCmdTemplate("deep_warm_ocean"));
        assertCmd("tptobiome @a minecraft:the_end",
                new TeleportToBiomeCmdTemplate(CmdTarget.ALL_PLAYERS, "minecraft:the_end"));

        try { new TeleportToBiomeCmdTemplate(" "); throw new AssertionError(); }
        catch (IllegalArgumentException e) { System.out.println("[校验] 空白 biomeId 拒绝 = PASS"); }
        System.out.println("全部 PASS");
    }

    private static void assertCmd(String expected, TeleportToBiomeCmdTemplate t) {
        if (!expected.equals(t.toString())) {
            throw new AssertionError("期望: " + expected + " 实际: " + t);
        }
    }
}