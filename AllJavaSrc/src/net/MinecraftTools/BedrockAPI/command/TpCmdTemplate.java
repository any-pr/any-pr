package net.MinecraftTools.BedrockAPI.command;

/**
 * {@code /tp} 命令模板 —— 对应 Bedrock 的 {@code TpCmdTemplate}。
 *
 * <h2>逆向实证</h2>
 * <ul>
 *   <li><b>前缀字面量</b> = <code>"/tp "</code>（地址 0xacfb89f）+ 单空格串 <code>" "</code>（地址 0xa83d0b0）</li>
 *   <li><b>唯一构造函数</b> = {@code TpCmdTemplate(CmdTarget, BlockPos)}，688 字节</li>
 *   <li>把 BlockPos 直接格式化成 <code>tp <target> <x> <y> <z></code></li>
 * </ul>
 *
 * <p>与 {@link TeleportCmdTemplate} 的区别：<code>/tp</code> 是 <code>/teleport</code> 的简写，
 * 仅支持基础坐标传送，不支持 facing/checkForBlocks 等扩展参数。
 *
 * @since 2026-09-27
 */
public final class TpCmdTemplate extends CmdTemplate {

    /** {@code tp <target> <x> <y> <z>} —— 直接用 long 坐标。 */
    public TpCmdTemplate(CmdTarget target, long x, long y, long z) {
        super("tp");
        append(target);
        append(Long.toString(x));
        append(Long.toString(y));
        append(Long.toString(z));
    }

    /** {@code tp <target> <x> <y> <z>} —— BlockPos 对象重载。 */
    public TpCmdTemplate(CmdTarget target, BlockPos pos) {
        this(target, pos.x(), pos.y(), pos.z());
    }

    public static void main(String[] args) {
        System.out.println("=== TpCmdTemplate 测试 ===");
        System.out.println("[1] 整数坐标 = " + new TpCmdTemplate(CmdTarget.SELF, 100, 64, -200));
        System.out.println("[2] BlockPos   = " + new TpCmdTemplate(CmdTarget.ALL_PLAYERS, new BlockPos(0, 100, 0)));
        System.out.println("[3] 负坐标     = " + new TpCmdTemplate(CmdTarget.of("@p"), -30_000_000L, 0L, 30_000_000L));
        System.out.println("[4] toSlashCmd = " + new TpCmdTemplate(CmdTarget.SELF, 1, 2, 3).toSlashCommand());

        // .rodata 向量回归（仅验证格式前缀，浮点会被截断为整数）
        TpCmdTemplate t = new TpCmdTemplate(CmdTarget.SELF, 389L, 74L, 28L);
        assertCmd("tp @s 389 74 28", t);
        System.out.println("全部 PASS");
    }

    private static void assertCmd(String expected, TpCmdTemplate t) {
        String actual = t.toString();
        if (!expected.equals(actual)) {
            throw new AssertionError("期望: " + expected + " 实际: " + actual);
        }
    }

    /** 最小 BlockPos record，仅用于本类。 */
    public record BlockPos(long x, long y, long z) {
        @Override
        public String toString() {
            return x + " " + y + " " + z;
        }
    }
}