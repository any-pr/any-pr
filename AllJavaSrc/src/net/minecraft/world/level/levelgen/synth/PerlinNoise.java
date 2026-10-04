package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import org.jspecify.annotations.Nullable;

public class PerlinNoise {
    private boolean isBedrockMode() {
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        return config != null && ("Bedrock-Edition".equals(config.farlandsStyle));
    }

    private boolean is1_18Exp4Mode() {
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        return config != null && ("1.18-exp-32bit".equals(config.precisionMode) || "1.18-exp-64bit".equals(config.precisionMode));
    }
    
    private static boolean limitReturnValueMode() {
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        return config != null && config.limitReturnValue;
    }

    /** 🔧 静态 Bedrock 模式判定（供 static wrap / wrapExact 使用） */
    private static boolean isBedrockStatic() {
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        return config != null && ("Bedrock-Edition".equals(config.farlandsStyle));
    }

    private static final int ROUND_OFF = 33554432;
    /** 🔧 MCRe 精确路径：折叠周期 2^25 = 33554432（double 表示精确无损）。 */
    private static final BigDecimal PERIOD_BD = BigDecimal.valueOf(33554432L);
    private static final BigDecimal HALF_BD = BigDecimal.valueOf(0.5);
    private static final BigDecimal TWO_BD = BigDecimal.valueOf(2L);
    /** 🔧 MCRe 精确路径：lowestFreqInputFactor 的精确值缓存（BigDecimal 不可变，惰性初始化并发安全）。 */
    private BigDecimal inputFactorExact;
    private final @Nullable ImprovedNoise[] noiseLevels;
    private final int firstOctave;
    private final DoubleList amplitudes;
    private final double lowestFreqValueFactor;
    private final double lowestFreqInputFactor;
    private final double maxValue;

    @Deprecated
    public static PerlinNoise createLegacyForBlendedNoise(final RandomSource random, final IntStream octaves) {
        return new PerlinNoise(random, makeAmplitudes(new IntRBTreeSet(octaves.boxed().collect(ImmutableList.toImmutableList()))), false);
    }

    @Deprecated
    public static PerlinNoise createLegacyForLegacyNetherBiome(final RandomSource random, final int firstOctave, final DoubleList amplitudes) {
        return new PerlinNoise(random, Pair.of(firstOctave, amplitudes), false);
    }

    public static PerlinNoise create(final RandomSource random, final IntStream octaves) {
        return create(random, octaves.boxed().collect(ImmutableList.toImmutableList()));
    }

    public static PerlinNoise create(final RandomSource random, final List<Integer> octaveSet) {
        return new PerlinNoise(random, makeAmplitudes(new IntRBTreeSet(octaveSet)), true);
    }

    // 修改：在 Bedrock 模式下截断振幅为 float 精度
    public static PerlinNoise create(final RandomSource random, final int firstOctave, final double firstAmplitude, final double amplitudes) {
        DoubleArrayList amplitudeList = new DoubleArrayList();
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        boolean isBedrock = config != null && "Bedrock-Edition".equals(config.farlandsStyle);
        if (isBedrock) {
            // 强制截断为 32 位 float，模拟单精度输入
            amplitudeList.add((float) firstAmplitude);
            amplitudeList.add((float) amplitudes);
        } else {
            amplitudeList.add(firstAmplitude);
            amplitudeList.add(amplitudes);
        }
        return new PerlinNoise(random, Pair.of(firstOctave, amplitudeList), true);
    }

    // 修改：在 Bedrock 模式下截断已有的 DoubleList 中的每个元素
    public static PerlinNoise create(final RandomSource random, final int firstOctave, final DoubleList amplitudes) {
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        boolean isBedrock = config != null && "Bedrock-Edition".equals(config.farlandsStyle);
        DoubleList finalAmplitudes = amplitudes;
        if (isBedrock) {
            DoubleArrayList truncated = new DoubleArrayList(amplitudes.size());
            for (int i = 0; i < amplitudes.size(); i++) {
                truncated.add((float) amplitudes.getDouble(i));
            }
            finalAmplitudes = truncated;
        }
        return new PerlinNoise(random, Pair.of(firstOctave, finalAmplitudes), true);
    }

    private static Pair<Integer, DoubleList> makeAmplitudes(final IntSortedSet octaveSet) {
        if (octaveSet.isEmpty()) {
            throw new IllegalArgumentException("Need some octaves!");
        }

        int lowFreqOctaves = -octaveSet.firstInt();
        int highFreqOctaves = octaveSet.lastInt();
        int octaves = lowFreqOctaves + highFreqOctaves + 1;
        if (octaves < 1) {
            throw new IllegalArgumentException("Total number of octaves needs to be >= 1");
        }

        DoubleList amplitudes = new DoubleArrayList(new double[octaves]);
        IntBidirectionalIterator iterator = octaveSet.iterator();

        while (iterator.hasNext()) {
            int octave = iterator.nextInt();
            amplitudes.set(octave + lowFreqOctaves, 1.0);
        }

        return Pair.of(-lowFreqOctaves, amplitudes);
    }

    protected PerlinNoise(final RandomSource random, final Pair<
                    Integer, DoubleList> pair, final boolean useNewInitialization) {
        this.firstOctave = pair.getFirst();
        this.amplitudes = pair.getSecond();
        int octaves = this.amplitudes.size();
        int zeroOctaveIndex = -this.firstOctave;
        this.noiseLevels = new ImprovedNoise[octaves];

        // --- 提前获取 Bedrock 模式状态（避免在后续循环中重复调用实例方法） ---
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        boolean isBedrock = config != null && "Bedrock-Edition".equals(config.farlandsStyle);

        if (useNewInitialization) {
            PositionalRandomFactory positional = random.forkPositional();
            for (int i = 0; i < octaves; i++) {
                if (this.amplitudes.getDouble(i) != 0.0) {
                    int octave = this.firstOctave + i;
                    this.noiseLevels[
                    i] = new ImprovedNoise(positional.fromHashOf("octave_" + octave));
                }
            }
        } else {
            // === 旧版初始化逻辑（保持不变） ===
            ImprovedNoise zeroOctave = new ImprovedNoise(random);
            if (zeroOctaveIndex >= 0 && zeroOctaveIndex < octaves) {
                double zeroOctaveAmplitude = this.amplitudes.getDouble(zeroOctaveIndex);
                if (zeroOctaveAmplitude != 0.0) {
                    this.noiseLevels[zeroOctaveIndex] = zeroOctave;
                }
            }

            for (int i = zeroOctaveIndex - 1; i >= 0; i--) {
                if (i < octaves) {
                    double amplitude = this.amplitudes.getDouble(i);
                    if (amplitude != 0.0) {
                        this.noiseLevels[i] = new ImprovedNoise(random);
                    } else {
                        skipOctave(random);
                    }
                } else {
                    skipOctave(random);
                }
            }

            if (Arrays.stream(this.noiseLevels).filter(Objects
                            ::nonNull).count() != this.amplitudes.stream().filter(a -> a != 0.0).count()) {
                throw new IllegalStateException("Failed to create correct number of noise levels for given non-zero amplitudes");
            }

            if (zeroOctaveIndex < octaves - 1) {
                throw new IllegalArgumentException("Positive octaves are temporarily disabled");
            }
        }

        // === 核心修改：计算倍频因子时强制截断为 float 精度 ===
        double rawInputFactor = Math.pow(2.0, -zeroOctaveIndex);
        double rawValueFactor = Math.pow(2.0, octaves - 1) / (Math.pow(2.0, octaves) - 1.0);

        if (isBedrock) {
            // 模拟 26.3 快照：构造时就将因子截断为 32 位 float 残值，再存入 double 字段
            this.lowestFreqInputFactor = (float) rawInputFactor;
            this.lowestFreqValueFactor = (float) rawValueFactor;
        } else {
            this.lowestFreqInputFactor = rawInputFactor;
            this.lowestFreqValueFactor = rawValueFactor;
        }

        // maxValue 通过 edgeValue 计算，而 edgeValue 内部已根据 isBedrockMode() 自动切换精度
        // 因此此处无需额外强转，其结果自然符合 Bedrock 模式的截断规则
        this.maxValue = this.edgeValue(2.0);
    }

    // 修改：Bedrock 模式下重新计算并截断
    protected double maxValue() {
        if (isBedrockMode()) {
            return (float) edgeValue(2.0);
        }
        return this.maxValue;
    }

    private static void skipOctave(final RandomSource random) {
        random.consumeCount(262);
    }

    public double getValue(final double x, final double y, final double z) {
        return this.getValue(x, y, z, 0.0, 0.0);
    }

    @Deprecated
    public double getValue(final double x, final double y, final double z, final double yScale, final double yFudge) {
        if (isBedrockMode()) {
            // === Bedrock 模式：全部使用 float 精度计算 ===
            float fx = (float) x;
            float fy = (float) y;
            float fz = (float) z;
            float fyScale = (float) yScale;
            float fyFudge = (float) yFudge;

            float value = 0.0f;
            float factor = (float) this.lowestFreqInputFactor;
            float valueFactor = (float) this.lowestFreqValueFactor;

            for (int i = 0; i < this.noiseLevels.length; i++) {
                ImprovedNoise noise = this.noiseLevels[i];
                if (noise != null) {
                    // 注意 wrap 返回 double，但此处我们强制转为 float 参与乘法，模拟单精度计算
                    float wrapX = (float) wrap(fx * factor);
                    float wrapY = (float) wrap(fy * factor);
                    float wrapZ = (float) wrap(fz * factor);
                    float noiseVal = (float) noise.noise(wrapX, wrapY, wrapZ, fyScale * factor, fyFudge * factor);
                    value += (float) (this.amplitudes.getDouble(i) * noiseVal * valueFactor);
                }
                factor *= 2.0f;
                valueFactor /= 2.0f;
            }
            return (float) value;
        }

        // === 原 double 实现 ===
        double value = 0.0;
        double factor = this.lowestFreqInputFactor;
        double valueFactor = this.lowestFreqValueFactor;

        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise noise = this.noiseLevels[i];
            if (noise != null) {
                double noiseVal = noise.noise(wrap(x * factor), wrap(y * factor), wrap(z * factor), yScale * factor, yFudge * factor);
                value += this.amplitudes.getDouble(i) * noiseVal * valueFactor;
            }

            factor *= 2.0;
            valueFactor /= 2.0;
        }

        return value;
    }

    /**
     * 🔧 MCRe「使用 BigDecimal / BigInteger 重写地形」——精确版 {@code getValue}。
     *
     * <p>与原版逐句对应，只把「坐标 × factor（2 的幂）」「wrap 折叠」换成精确运算：
     * <pre>
     *   原版：wrap(x * factor) —— factor 每层 ×2，把上一层的舍入误差一路放大 2^15 倍，
     *         再经过 wrap 的大数相减（灾难性抵消）→ 折叠结果只剩几个离散台阶 → 地形拉伸
     *   精确：BigDecimal 全程无损，折叠用精确取模 → 折叠结果严格随坐标逐格变化 ✓
     * </pre>
     * 幅度加权（amplitudes × valueFactor）与噪声输出仍用 double —— 输出值域小，精度绰绰有余。
     */
    public double getValueExact(final BigDecimal x, final BigDecimal y, final BigDecimal z) {
        return this.getValueExact(x, y, z, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public double getValueExact(
            final BigDecimal x,
            final BigDecimal y,
            final BigDecimal z,
            final BigDecimal yScale,
            final BigDecimal yFudge) {
        // 🔧 Bedrock 模式适配：输入先量化到 float（模拟基岩单精度输入坐标），再精确计算
        // float 的值可精确表示为 BigDecimal → 精确计算无灾难性抵消 → 无地形拉伸
        final BigDecimal bx = isBedrockMode() ? BigDecimal.valueOf(x.floatValue()) : x;
        final BigDecimal by = isBedrockMode() ? BigDecimal.valueOf(y.floatValue()) : y;
        final BigDecimal bz = isBedrockMode() ? BigDecimal.valueOf(z.floatValue()) : z;
        double value = 0.0;
        BigDecimal factor = this.exactInputFactor();
        double valueFactor = this.lowestFreqValueFactor;

        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise noise = this.noiseLevels[i];
            if (noise != null) {
                final BigDecimal factorYScale = yScale.multiply(factor);
                final BigDecimal factorYFudge = yFudge.multiply(factor);
                final double noiseVal = noise.noiseExact(
                        wrapExact(bx.multiply(factor)),
                        wrapExact(by.multiply(factor)),
                        wrapExact(bz.multiply(factor)),
                        factorYScale,
                        factorYFudge);
                value += this.amplitudes.getDouble(i) * noiseVal * valueFactor;
            }

            factor = factor.multiply(TWO_BD);
            valueFactor /= 2.0;
        }

        return value;
    }

    private BigDecimal exactInputFactor() {
        BigDecimal v = this.inputFactorExact;
        if (v == null) {
            v = new BigDecimal(this.lowestFreqInputFactor);
            this.inputFactorExact = v;
        }
        return v;
    }

    public double maxBrokenValue(final double yScale) {
        if (isBedrockMode()) {
            float fYScale = (float) yScale;
            float val = (float) edgeValue(fYScale + 2.0f);
            return (float) val;
        }
        return edgeValue(yScale + 2.0);
    }

    private double edgeValue(final double noiseValue) {
        if (isBedrockMode()) {
            float fNoise = (float) noiseValue;
            float value = 0.0f;
            float valueFactor = (float) this.lowestFreqValueFactor;
            for (int i = 0; i < this.noiseLevels.length; i++) {
                ImprovedNoise noise = this.noiseLevels[i];
                if (noise != null) {
                    value += (float) (this.amplitudes.getDouble(i) * fNoise * valueFactor);
                }
                valueFactor /= 2.0f;
            }
            return (float) value;
        }

        double value = 0.0;
        double valueFactor = this.lowestFreqValueFactor;
        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise noise = this.noiseLevels[i];
            if (noise != null) {
                value += this.amplitudes.getDouble(i) * noiseValue * valueFactor;
            }
            valueFactor /= 2.0;
        }
        return value;
    }

    public @Nullable ImprovedNoise getOctaveNoise(final int i) {
        return this.noiseLevels[this.noiseLevels.length - 1 - i];
    }

    // === 坐标折叠函数（保持不变） ===
    public static double computeReleaseValue(double x) {
        long l = Mth.lfloor(x);
        x -= l;
        l %= 16777216L;
        return x + l;
    }

    public static double wrap(final double x) {
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        if (config == null) {
            return x;
        }
        double limitNoiseValue = config.limitReturnValueValue;
        String mode = config.precisionMode;
        // 🔧 Bedrock 模式：全 float 精度折叠（模拟基岩版坐标量级，边境之地形态与基岩一致）
        if (isBedrockStatic()) {
            float folded;
            switch (mode) {
                case "64bit":
                case "1.18-exp-64bit":
                    folded = (float) x - Mth.lfloor((float) x / 3.3554432E7F + 0.5F) * 3.3554432E7F;
                    break;
                case "Release":
                    folded = (float) computeReleaseValue((float) x);
                    break;
                default:
                    folded = (float) x;
                    break;
            }
            // 限制返回值（float 域，log10(0) = -Infinity 天然跳过）
            if (limitReturnValueMode()) {
                double abs = Math.abs((double) folded);
                if (Math.log10(abs) > limitNoiseValue) {
                    double logAbs = Math.log10(abs);
                    folded = (float) (Math.pow(10, logAbs - Math.floor(logAbs - limitNoiseValue)) * Math.signum(folded));
                }
            }
            return (float) folded;
        }
        double folded;
        switch (mode) {
            case "64bit":
            case "1.18-exp-64bit":
                folded = x - Mth.lfloor(x / 3.3554432E7 + 0.5) * 3.3554432E7;
                break;
            case "Release":
                folded = computeReleaseValue(x);
                break;
            default:
                folded = x;
                break;
        }
        // 🔧 限制逻辑：限制输入坐标量级（对数域折叠），支持任意实数等级（含小数、0）
        if (limitReturnValueMode()) {
            double abs = Math.abs(folded);
            // log10(0) = -Infinity，恒不满足 > limit，天然跳过，无需特判 0
            if (Math.log10(abs) > limitNoiseValue) {
                double logAbs = Math.log10(abs);
                folded = Math.pow(10, logAbs - Math.floor(logAbs - limitNoiseValue)) * Math.signum(folded);
            }
        }
        return folded;
    }

    /**
     * 🔧 MCRe「使用 BigDecimal / BigInteger 重写地形」——精确版 {@link #wrap(double)}。
     *
     * <p>折叠的数学含义是「把坐标折回周期内」（取模）。原版用 double 做
     * {@code x - lfloor(x / P + 0.5) * P}：当 |x| 大到 ULP &gt; 1 时，高位相减会把低位
     * 全部吃掉（<b>灾难性抵消</b>）→ 折叠结果被量化成几个离散台阶 → <b>地形拉伸</b>。
     * 精确版直接做精确取模，彻底消除这一损失。
     *
     * <p>各模式与原版一一对应；「限制返回值」分支保留原版的 double 近似
     * （它本身就是一个量级近似功能，且结果只用于限制输入范围）。
     */
    public static BigDecimal wrapExact(final BigDecimal x) {
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        if (config == null) {
            return x;
        }
        double limitNoiseValue = config.limitReturnValueValue;
        String mode = config.precisionMode;
        // 🔧 Bedrock 模式：先转 float（单精度输入坐标，模拟基岩输入量级），再用 BigDecimal 精确取模
        // —— 避免地形拉伸（精确取模消除灾难性抵消）+ 完美模拟基岩版边境之地（float 输入量级）
        if (isBedrockStatic()) {
            final float fx = x.floatValue();
            BigDecimal folded;
            switch (mode) {
                case "64bit":
                case "1.18-exp-64bit": {
                    // ① 商：BigDecimal 精确除法（不丢精度）
                    final BigDecimal q = BigDecimal.valueOf(fx).divide(PERIOD_BD, ExactNoiseMath.DIVISION).add(HALF_BD);
                    final long l = ExactNoiseMath.floorToLongSaturated(q);
                    // ② 折叠：BigDecimal 精确相减（避免 float 减法的灾难性抵消 → 无地形拉伸）
                    folded = BigDecimal.valueOf(fx).subtract(BigDecimal.valueOf(l).multiply(PERIOD_BD));
                    break;
                }
                case "Release": {
                    // float 输入 + 精确小数分离
                    final long l = ExactNoiseMath.floorToLongSaturated(BigDecimal.valueOf(fx));
                    final BigDecimal frac = BigDecimal.valueOf(fx).subtract(BigDecimal.valueOf(l));
                    folded = frac.add(BigDecimal.valueOf(l % 16777216L));
                    break;
                }
                default:
                    folded = BigDecimal.valueOf(fx);
                    break;
            }
            if (limitReturnValueMode()) {
                final double fd = folded.doubleValue();
                final double abs = Math.abs(fd);
                if (Math.log10(abs) > limitNoiseValue) {
                    final double logAbs = Math.log10(abs);
                    folded = BigDecimal.valueOf(Math.pow(10, logAbs - Math.floor(logAbs - limitNoiseValue)) * Math.signum(fd));
                }
            }
            return folded;
        }
        BigDecimal folded;
        switch (mode) {
            case "64bit":
            case "1.18-exp-64bit": {
                // 原版：x - Mth.lfloor(x / 3.3554432E7 + 0.5) * 3.3554432E7
                final BigDecimal q = x.divide(PERIOD_BD, ExactNoiseMath.DIVISION).add(HALF_BD);
                final long l = ExactNoiseMath.floorToLongSaturated(q);
                folded = x.subtract(BigDecimal.valueOf(l).multiply(PERIOD_BD));
                break;
            }
            case "Release": {
                // 原版：long l = Mth.lfloor(x); x -= l; l %= 16777216L; return x + l;
                final long l = ExactNoiseMath.floorToLongSaturated(x);
                final BigDecimal frac = x.subtract(BigDecimal.valueOf(l));
                folded = frac.add(BigDecimal.valueOf(l % 16777216L));
                break;
            }
            default:
                folded = x;
                break;
        }
        if (limitReturnValueMode()) {
            final double fd = folded.doubleValue();
            final double abs = Math.abs(fd);
            // log10(0) = -Infinity，恒不满足 > limit，天然跳过
            if (Math.log10(abs) > limitNoiseValue) {
                final double logAbs = Math.log10(abs);
                folded = BigDecimal.valueOf(Math.pow(10, logAbs - Math.floor(logAbs - limitNoiseValue)) * Math.signum(fd));
            }
        }
        return folded;
    }

    protected int firstOctave() {
        return this.firstOctave;
    }

    protected DoubleList amplitudes() {
        return this.amplitudes;
    }

    @VisibleForTesting
    public void parityConfigString(final StringBuilder sb) {
        sb.append("PerlinNoise{");
        List<
                String> amplitudeStrings = this.amplitudes.stream().map(d -> String.format(Locale.ROOT, "%.2f", d)).toList();
        sb.append("first octave: ").append(this.firstOctave).append(", amplitudes: ").append(amplitudeStrings).append(", noise levels: [");

        for (int i = 0; i < this.noiseLevels.length; i++) {
            sb.append(i).append(": ");
            ImprovedNoise noiseLevel = this.noiseLevels[i];
            if (noiseLevel == null) {
                sb.append("null");
            } else {
                noiseLevel.parityConfigString(sb);
            }

            sb.append(", ");
        }

        sb.append("]");
        sb.append("}");
    }
}