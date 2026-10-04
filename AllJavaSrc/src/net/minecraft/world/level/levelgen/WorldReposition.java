package net.minecraft.world.level.levelgen;

import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.MathContext;
import net.minecraft.core.Direction;

/**
 * 🔧 MCRe NoiseFarlands —— 世界生成器偏移/缩放工具类
 *
 * <p>移植自 UltimateScaler (MIT, inf32768) 的 mixin 偏移逻辑，
 * 用我们的自研 BigDecimal (DynamicAccuracy 库) 实现"无大小限制"——
 * 避免之前用 Float256 时超大 scale/shift 转 int/long 触发 Int256 溢出崩溃
 * （{@code ArithmeticException: Int256 out of long range}）。
 *
 * <p>核心公式（一维）：{@code newPos = pos * scale + shift}
 *
 * <p>接入点：DensityFunctions.Noise/ShiftedNoise/Shift/ShiftA/ShiftB/YClampedGradient.compute，
 * BlendedNoise.compute，NoiseBasedChunkGenerator.createFluidPicker。
 *
 * <p>缓存策略：开世界时（WorldMainSettingScreen.onDone）调用 {@link #refresh} 一次性解析为 BigDecimal，
 * 运行期直接读内存数组，避免每个方块解析字符串。
 *
 * <p>本类位于 shared 包（levelgen），不依赖 client 包，避免循环依赖。
 */
public final class WorldReposition {
    /** 缩放因子（X, Y, Z），默认全 1（无缩放） */
    private static final BigDecimal[] SCALE = {BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE};
    /** 偏移量（X, Y, Z），默认全 0（无偏移） */
    private static final BigDecimal[] SHIFT = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    /** YClampedGradient 独立开关——默认 false（保持原版 Y 轴海拔梯度，避免边境之地消失） */
    private static volatile boolean yClampedGradientOffsetEnabled = false;
    /** 表面噪声与规则偏移开关——默认 true（SurfaceSystem/SurfaceRules 也应用偏移缩放） */
    private static volatile boolean surfaceNoiseOffsetEnabled = true;

    // ──────── 🔧 MCRe 性能：热路径零分配优化 ────────
    /**
     * 恒等变换位掩码——bit i 置位表示轴 i 的 {@code scale == 1 && shift == 0}。
     * <p>此时 {@code pos * 1 + 0 == pos} 精确成立，可**零分配直通**（省掉 BigDecimal 构造/乘法/加法）。
     * <p>热路径影响：{@code DensityFunctions.Noise/Shift/...compute} 每个噪声采样点都会调用，
     * 每区块百万级调用 → 每调用 3~4 次 BigDecimal 分配是巨大的 GC 压力。
     */
    private static volatile int identityMask = 0b111;
    /** saturate 比较用的常量（缓存，避免每次调用 BigDecimal.valueOf(Double.MAX_VALUE) 分配） */
    private static final BigDecimal DOUBLE_MAX = BigDecimal.valueOf(Double.MAX_VALUE);
    private static final BigDecimal NEG_DOUBLE_MAX = BigDecimal.valueOf(-Double.MAX_VALUE);

    private WorldReposition() {
    }

    // ═════════════════ 同步入口 ═════════════════

    /**
     * 🔧 从 {@link RepositionConfig} 刷新内存缓存（开世界时调用一次）。
     * 调用方负责把 String 解析为 BigDecimal（用 {@link #parseOrFallback}），本类不接触字符串。
     */
    public static void refresh(final RepositionConfig config) {
        SCALE[0] = config.scaleX();
        SCALE[1] = config.scaleY();
        SCALE[2] = config.scaleZ();
        SHIFT[0] = config.shiftX();
        SHIFT[1] = config.shiftY();
        SHIFT[2] = config.shiftZ();
        yClampedGradientOffsetEnabled = config.yClampedGradientOffset();
        surfaceNoiseOffsetEnabled = config.surfaceNoiseOffset();
        // 🔧 MCRe 性能：一次性算好恒等掩码（运行期热路径只读一个 volatile int）
        int mask = 0;
        for (int i = 0; i < 3; i++) {
            if (SCALE[i].compareTo(BigDecimal.ONE) == 0 && SHIFT[i].signum() == 0) {
                mask |= 1 << i;
            }
        }
        identityMask = mask;
    }

    // ═════════════════ 一维变换（无损 BigDecimal） ═════════════════

    /** 一维变换 → BigDecimal（无损，公式：{@code newPos = pos * scale + shift}） */
    public static BigDecimal reposition(final BigDecimal pos, final Direction.Axis axis) {
        final int i = axis.ordinal();
        // 🔧 MCRe 性能：恒等变换时直通（省掉 multiply + add 两次分配）
        if ((identityMask & (1 << i)) != 0) {
            return pos;
        }
        return pos.multiply(SCALE[i]).add(SHIFT[i]);
    }

    // ═════════════════ 一维变换（带 saturate 防护的 double 输出） ═════════════════

    /**
     * 一维变换 → double。BigDecimal 超 ±Double.MAX_VALUE 时 saturate 到 ±MAX_VALUE，
     * 避免 Infinity/NaN 传到 Minecraft 内部触发 NaN 链式崩溃。
     * <p>🔧 MCRe 性能：恒等变换（scale==1 && shift==0）时零分配直通——这是最高频路径。
     */
    public static double reposition(final double pos, final Direction.Axis axis) {
        final int i = axis.ordinal();
        if ((identityMask & (1 << i)) != 0) {
            return pos;
        }
        return toDoubleSaturated(BigDecimal.valueOf(pos).multiply(SCALE[i]).add(SHIFT[i]));
    }

    /** 一维变换 → double（int 输入） */
    public static double reposition(final int pos, final Direction.Axis axis) {
        final int i = axis.ordinal();
        if ((identityMask & (1 << i)) != 0) {
            return pos;
        }
        return toDoubleSaturated(BigDecimal.valueOf(pos).multiply(SCALE[i]).add(SHIFT[i]));
    }

    /** 一维变换 → double（long 输入） */
    public static double reposition(final long pos, final Direction.Axis axis) {
        final int i = axis.ordinal();
        if ((identityMask & (1 << i)) != 0) {
            return pos;
        }
        return toDoubleSaturated(BigDecimal.valueOf(pos).multiply(SCALE[i]).add(SHIFT[i]));
    }

    // ═════════════════ 逆运算（用于 createFluidPicker 把世界 Y 还原到玩家 Y） ═════════════════

    /**
     * 🔧 Y 轴逆运算：把世界生成器输出的 Y 还原到玩家世界 Y。
     * 公式：{@code playerY = (worldY - shift) / scale}
     * 仅当 enabledYClampedGradientOffset=true 时调用（开关关闭时上层不走此函数）。
     * 用 DECIMAL64 (16 位精度) 做除法避免非终止小数抛 ArithmeticException。
     */
    public static int inverseY(final int worldY) {
        final BigDecimal playerY = BigDecimal.valueOf(worldY)
                .subtract(SHIFT[1])
                .divide(SCALE[1], MathContext.DECIMAL64);
        return playerY.intValue();  // 截断
    }

    // ═════════════════ 开关访问 ═════════════════

    /**
     * YClampedGradient 是否启用偏移——控制 Y 轴 base stone 海拔梯度。
     * 启用后 Y 轴不会出现任何边境之地，作为可选项让用户自己权衡。
     */
    public static boolean isYClampedGradientOffsetEnabled() {
        return yClampedGradientOffsetEnabled;
    }

    /**
     * 🔧 MCRe：表面噪声与规则偏移开关——控制 SurfaceSystem/SurfaceRules 是否应用偏移缩放。
     * 默认 true；关闭时表面材质按原始坐标采样（等同 UltimateScaler 行为）。
     */
    public static boolean isSurfaceNoiseOffsetEnabled() {
        return surfaceNoiseOffsetEnabled;
    }

    /**
     * 🔧 MCRe：表面噪声专用一维变换（int → double）。
     * <p>开关关闭时直通返回原坐标（零开销，保持原版表面材质分布）；
     * 开关开启时走 {@link #reposition(int, Direction.Axis)}（含 saturate 防护）。
     */
    public static double repositionSurface(final int pos, final Direction.Axis axis) {
        return surfaceNoiseOffsetEnabled ? reposition(pos, axis) : pos;
    }

    /**
     * 🔧 MCRe：读指定轴的缩放因子（用于逆运算，如 {@link #inverseY} 把世界 Y 还原到玩家 Y）。
     */
    public static BigDecimal getScale(final Direction.Axis axis) {
        return SCALE[axis.ordinal()];
    }

    /**
     * 🔧 MCRe：读指定轴的偏移量（同上）。
     */
    public static BigDecimal getShift(final Direction.Axis axis) {
        return SHIFT[axis.ordinal()];
    }

    // ═════════════════ 解析 helper（公开，调用方复用） ═════════════════

    /**
     * 把字符串解析为 BigDecimal（自研 DynamicAccuracy 库，原生支持 e/E、负号、小数），失败回退到 fallback。
     */
    public static BigDecimal parseOrFallback(final String s, final BigDecimal fallback) {
        if (s == null || s.trim().isEmpty()) {
            return fallback;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ═════════════════ 配置快照 record ═════════════════

    /**
     * 🔧 MCRe：偏移/缩放配置快照——开世界时一次解析，运行期不再触碰字符串。
     * 位于 shared 包（levelgen），不引用 client 包，避免循环依赖。
     */
    public record RepositionConfig(
            BigDecimal scaleX, BigDecimal scaleY, BigDecimal scaleZ,
            BigDecimal shiftX, BigDecimal shiftY, BigDecimal shiftZ,
            boolean yClampedGradientOffset,
            boolean surfaceNoiseOffset
    ) {
        /** 默认无变换配置（scale=1, shift=0, yGradient=false, surfaceNoise=true） */
        public static final RepositionConfig DISABLED = new RepositionConfig(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                false, true
        );
    }

    // ═════════════════ 内部 saturate helper ═════════════════

    /**
     * 🔧 把 BigDecimal 转 double，超出 ±Double.MAX_VALUE 时 saturate 到 ±MAX_VALUE。
     * 避免 Minecraft 内部收到 Infinity/NaN 引发连锁崩溃。
     * <p>🔧 MCRe 性能：saturate 常量已缓存（原来每次调用都 BigDecimal.valueOf(Double.MAX_VALUE) 分配）；
     * 另加量级快路径——整数部分位数 ≤ 308 时必然 &lt; 10^308 &lt; 1.8e308，直接跳过两次 compareTo。
     * <p>⚠️ 判量级必须用 {@code precision() - scale()}（= 整数部分位数），
     * 只用 precision() 会把 {@code 1e309}（precision=1、scale=-309）误判为小数值。
     */
    private static double toDoubleSaturated(final BigDecimal value) {
        if (value.precision() - value.scale() > 308) {
            if (value.signum() > 0 && value.compareTo(DOUBLE_MAX) > 0) {
                return Double.MAX_VALUE;
            }
            if (value.signum() < 0 && value.compareTo(NEG_DOUBLE_MAX) < 0) {
                return -Double.MAX_VALUE;
            }
        }
        return value.doubleValue();
    }
}