package net.minecraft.world.level.levelgen.synth;

import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.MathContext;
import net.MinecraftTools.Math.DynamicAccuracy.RoundingMode;
import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;

/**
 * 🔧 MCRe NoiseFarlands —— 「使用 BigDecimal / BigInteger 重写地形」精确数学工具。
 *
 * <h2>为什么需要它（地形拉伸的成因，2026-09-25 实测锤）</h2>
 * 原版噪声链用的是 {@code double}（53 位尾数）。链路是：
 * <pre>
 *   blockX(整数) → WorldReposition(×scale+shift) → × xzMultiplier(171.103) → × 2^k(每个 octave)
 *   → PerlinNoise.wrap(折叠，周期 2^25) → ImprovedNoise: x = folded + xo → floor / 小数部分 xr
 * </pre>
 * 每一步都会把上一层的舍入误差**放大**，最致命的是 <b>wrap 折叠的灾难性抵消</b>：
 * 折叠是「减去 2^25 的整数倍」，当被折叠值大到 ULP 已经 &gt; 1 时，高位相减后低位全是噪声——
 * 折叠结果只剩几个离散台阶，{@code xr} 不再随方块坐标逐格变化 → <b>地形被量化成台阶（拉伸）</b>。
 *
 * <p>实测（pos × 171.103 × 2^15 → wrap(2^25) → +xo → floor → xr）：
 * <pre>
 *   pos = 1e15：double 的 xr 只在 3 个固定值之间跳（0.45678901234499847 / …206701994 / …299834250）
 *              精确链路 xr 每格 +0.104 线性递增（0.519 → 0.623 → 0.727 → 0.831 → 0.935）✓
 * </pre>
 *
 * <h2>本类的职责</h2>
 * 提供「精确运算 → 复刻原版 double 语义」的桥接工具，特别是：
 * <ul>
 *   <li>{@link #floorToIntSaturated} —— 复刻 {@code (int)Math.floor(v)} 的<b>饱和</b>语义
 *       （正是它造就了「平面边境之地」：坐标超过 ±2^31 后全部落在同一晶格角，<b>必须保留</b>）；</li>
 *   <li>{@link #floorToLongSaturated} —— 复刻 {@code (long)Math.floor(v)}；</li>
 *   <li>{@link #of(double)} —— 把 double 常量（如 171.103、1.0181268882175227）解释为它的
 *       <b>精确 IEEE 754 值</b>，保证「精确重写」是 double 语义的严格超集；</li>
 *   <li>{@link #enabled} / {@link #needsExact} —— 开关 + 性能闸门。</li>
 * </ul>
 *
 * <h2>性能闸门</h2>
 * 精确路径每样本要做几十次 BigDecimal 运算（微秒级），全量走会让区块生成慢到不可玩。
 * 因此只有当坐标大到 double 的绝对误差已经能影响地形（&gt; 0.5 格）时才切换：
 * <pre>
 *   误差(v) ≈ v × 2^-53，要求 &lt; 0.5 且 v = |limitX| × 2^15（16 个 octave 的最高频）
 *   ⇒ |limitX| &lt; 2^53 / 2^16 = 2^37 ≈ 1.37e11
 * </pre>
 * 取 {@link #EXACT_THRESHOLD_XZ} = 2^36（6.9e10，留一倍余量）。近处完全走原 double 快路径，零开销。
 */
public final class ExactNoiseMath {
    /** 精确除法精度：34 位有效数字（double 只有 16 位，足够任何 floor/折叠判定）。 */
    public static final MathContext DIVISION = MathContext.DECIMAL128;

    /**
     * 精确路径切换阈值（针对已乘过 xzMultiplier 的坐标）。
     * <p>{@code 2^36 = 68719476736}；对应原版方块坐标约 4.0e8（≈4 亿格）。
     * <p>低于它时 double 的绝对误差 &lt; 0.5 × 2^-16 格，地形完全正确。
     */
    public static final double EXACT_THRESHOLD_XZ = 6.8719476736E10;
    /** Y 轴阈值（yMultiplier 只有 XZ 的一半，故阈值可放宽一倍）。 */
    public static final double EXACT_THRESHOLD_Y = 1.37438953472E11;

    private static final BigDecimal INT_MAX_BD = BigDecimal.valueOf(Integer.MAX_VALUE);
    private static final BigDecimal INT_MIN_BD = BigDecimal.valueOf(Integer.MIN_VALUE);
    private static final BigDecimal LONG_MAX_BD = BigDecimal.valueOf(Long.MAX_VALUE);
    private static final BigDecimal LONG_MIN_BD = BigDecimal.valueOf(Long.MIN_VALUE);

    private ExactNoiseMath() {
    }

    // ═════════════════ 开关 ═════════════════

    /** 配置开关：「使用 BigDecimal / BigInteger 重写地形」。 */
    public static boolean enabled() {
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        return config != null && config.exactTerrainRewrite;
    }

    /** 性能闸门：坐标是否已经大到 double 会失真（需要切精确路径）。 */
    public static boolean needsExact(final double repositionedX, final double repositionedY, final double repositionedZ,
                                     final double xzMultiplier, final double yMultiplier) {
        return Math.abs(repositionedX * xzMultiplier) > EXACT_THRESHOLD_XZ
                || Math.abs(repositionedZ * xzMultiplier) > EXACT_THRESHOLD_XZ
                || Math.abs(repositionedY * yMultiplier) > EXACT_THRESHOLD_Y;
    }

    // ═════════════════ double ⇄ 精确值桥接 ═════════════════

    /** double → 精确值（IEEE 754 位精确转换，不是十进制字面量）。 */
    public static BigDecimal of(final double v) {
        return new BigDecimal(v);
    }

    /** float → 精确值（先用 float 截断再精确展开，用于复刻原版的 {@code 1.0E-7F} 之类字面量）。 */
    public static BigDecimal ofFloat(final float v) {
        return new BigDecimal((double) v);
    }

    /** int → 精确值。 */
    public static BigDecimal of(final int v) {
        return BigDecimal.valueOf(v);
    }

    /** long → 精确值。 */
    public static BigDecimal of(final long v) {
        return BigDecimal.valueOf(v);
    }

    /** 精确值 → double（复刻 {@code (double)} 转换，超范围自然得到 ±Infinity，与 double 语义一致）。 */
    public static double toDouble(final BigDecimal v) {
        return v.doubleValue();
    }

    /**
     * double → 精确值（带缓存）。
     * <p>用于热路径里的**固定系数**（如 {@code DensityFunctions.Noise} 的 xzScale/yScale）：
     * 同一个系数会被每区块数万次采样复用，缓存后避免每次样本都做位展开。
     * <p>缓存规模极小（系数种类是个位数），用 {@link java.util.concurrent.ConcurrentHashMap}
     * 保证多线程区块生成下的安全。
     */
    private static final java.util.concurrent.ConcurrentHashMap<Double, BigDecimal> DOUBLE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static BigDecimal cachedOf(final double v) {
        return DOUBLE_CACHE.computeIfAbsent(v, BigDecimal::new);
    }

    // ═════════════════ 复刻原版取整语义（保留溢出！） ═════════════════

    /**
     * 复刻 {@code (int) Math.floor(v)}。
     * <p><b>饱和语义必须保留</b>：这正是「平面边境之地」的来源——噪声晶格索引超出 ±2^31 后
     * 全部钳到 {@code Integer.MAX_VALUE / MIN_VALUE}，所有采样点落到同一晶格角 → 地形变平。
     * 若这里改成回绕（wrap）或抛异常，会破坏边境之地的成因。
     */
    public static int floorToIntSaturated(final BigDecimal v) {
        final BigDecimal f = v.setScale(0, RoundingMode.FLOOR);
        if (f.compareTo(INT_MAX_BD) >= 0) {
            return Integer.MAX_VALUE;
        }
        if (f.compareTo(INT_MIN_BD) <= 0) {
            return Integer.MIN_VALUE;
        }
        return f.intValue();
    }

    /**
     * 复刻 {@code (long) Math.floor(v)}（{@code Mth.lfloor}）。
     * <p>同样保留 long 饱和语义。
     */
    public static long floorToLongSaturated(final BigDecimal v) {
        final BigDecimal f = v.setScale(0, RoundingMode.FLOOR);
        if (f.compareTo(LONG_MAX_BD) >= 0) {
            return Long.MAX_VALUE;
        }
        if (f.compareTo(LONG_MIN_BD) <= 0) {
            return Long.MIN_VALUE;
        }
        return f.longValue();
    }

    /** 精确的向下取整（不饱和，返回精确整数）。 */
    public static BigDecimal floorExact(final BigDecimal v) {
        return v.setScale(0, RoundingMode.FLOOR);
    }
}
