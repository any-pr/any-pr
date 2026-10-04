package me.noisefarlands.mcbig.Math;

import me.noisefarlands.mcbig.util.DynamicNumber;
import me.noisefarlands.mcbig.util.Int256;
import me.noisefarlands.mcbig.util.NumberType;
// 在文件顶部（package 之后）添加：
import me.noisefarlands.mcbig.core.BigVec3;
import net.minecraft.util.RandomSource;

import java.math.BigInteger;

/**
 * 无限世界数学工具类，对标原版 net.minecraft.util.Mth。 所有方法均使用 DynamicNumber 作为参数和返回值，支持 Long / Int256 /
 * BigInteger 三种底层。
 *
 * <p>随机方法基于原版 RandomSource，但返回 DynamicNumber，支持超大范围（超过 long 时使用 BigInteger）。
 */
public final class BigMath {

    // ---------- 常量 ----------
    public static final DynamicNumber PI = DynamicNumber.of(Math.PI);
    public static final DynamicNumber HALF_PI = DynamicNumber.of(Math.PI / 2);
    public static final DynamicNumber TWO_PI = DynamicNumber.of(Math.PI * 2);
    public static final DynamicNumber DEG_TO_RAD = DynamicNumber.of(Math.PI / 180.0);
    public static final DynamicNumber RAD_TO_DEG = DynamicNumber.of(180.0 / Math.PI);
    public static final DynamicNumber EPSILON = DynamicNumber.of(1.0E-5);

    // ---------- 三角函数 ----------
    public static DynamicNumber sin(DynamicNumber x) {
        return DynamicNumber.of(Math.sin(x.doubleValue()));
    }

    public static DynamicNumber cos(DynamicNumber x) {
        return DynamicNumber.of(Math.cos(x.doubleValue()));
    }

    public static DynamicNumber tan(DynamicNumber x) {
        return DynamicNumber.of(Math.tan(x.doubleValue()));
    }

    public static DynamicNumber asin(DynamicNumber x) {
        return DynamicNumber.of(Math.asin(x.doubleValue()));
    }

    public static DynamicNumber acos(DynamicNumber x) {
        return DynamicNumber.of(Math.acos(x.doubleValue()));
    }

    public static DynamicNumber atan(DynamicNumber x) {
        return DynamicNumber.of(Math.atan(x.doubleValue()));
    }

    public static DynamicNumber atan2(DynamicNumber y, DynamicNumber x) {
        return DynamicNumber.of(Math.atan2(y.doubleValue(), x.doubleValue()));
    }

    // ---------- 平方根 / 幂 ----------
    public static DynamicNumber sqrt(DynamicNumber x) {
        return x.sqrt();
    }

    public static DynamicNumber invSqrt(DynamicNumber x) {
        return DynamicNumber.of(1.0 / Math.sqrt(x.doubleValue()));
    }

    public static DynamicNumber square(DynamicNumber x) {
        return x.multiply(x);
    }

    public static DynamicNumber cube(DynamicNumber x) {
        return x.multiply(x).multiply(x);
    }

    // ---------- 绝对值 ----------
    public static DynamicNumber abs(DynamicNumber x) {
        return x.abs();
    }

    // ---------- 取整 ----------
    public static DynamicNumber floor(DynamicNumber x) {
        return DynamicNumber.of(Math.floor(x.doubleValue()));
    }

    public static DynamicNumber ceil(DynamicNumber x) {
        return DynamicNumber.of(Math.ceil(x.doubleValue()));
    }

    public static DynamicNumber round(DynamicNumber x) {
        return DynamicNumber.of(Math.round(x.doubleValue()));
    }

    public static DynamicNumber frac(DynamicNumber x) {
        return x.subtract(floor(x));
    }

    public static DynamicNumber distanceSquared(BigVec3 a, BigVec3 b) {
        return distanceSquared(a.bigX(), a.bigY(), a.bigZ(),
        b.bigX(), b.bigY(), b.bigZ());
    }

    // ---------- 钳制 ----------
    public static DynamicNumber clamp(DynamicNumber value, DynamicNumber min, DynamicNumber max) {
        if (value.compareTo(min) < 0) return min;
        if (value.compareTo(max) > 0) return max;
        return value;
    }

    public static DynamicNumber clamp(DynamicNumber value, long min, long max) {
        return clamp(value, DynamicNumber.of(min), DynamicNumber.of(max));
    }

    // ---------- 线性插值 ----------
    public static DynamicNumber lerp(DynamicNumber alpha, DynamicNumber from, DynamicNumber to) {
        return from.add(alpha.multiply(to.subtract(from)));
    }

    public static DynamicNumber clampedLerp(DynamicNumber alpha, DynamicNumber from, DynamicNumber to) {
        if (alpha.compareTo(DynamicNumber.ZERO) < 0) return from;
        if (alpha.compareTo(DynamicNumber.ONE) > 0) return to;
        return lerp(alpha, from, to);
    }

    public static DynamicNumber inverseLerp(DynamicNumber value, DynamicNumber min, DynamicNumber max) {
        return value.subtract(min).divide(max.subtract(min));
    }

    // ---------- 角度处理 ----------
    public static DynamicNumber wrapDegrees(DynamicNumber angle) {
        double a = angle.doubleValue() % 360.0;
        if (a >= 180.0) a -= 360.0;
        if (a < -180.0) a += 360.0;
        return DynamicNumber.of(a);
    }

    public static DynamicNumber degreesDifference(DynamicNumber from, DynamicNumber to) {
        return wrapDegrees(to.subtract(from));
    }

    public static DynamicNumber rotLerp(DynamicNumber alpha, DynamicNumber from, DynamicNumber to) {
        return from.add(alpha.multiply(wrapDegrees(to.subtract(from))));
    }

    // ---------- 距离计算 ----------
    public static DynamicNumber lengthSquared(DynamicNumber x, DynamicNumber y, DynamicNumber z) {
        return square(x).add(square(y)).add(square(z));
    }

    public static DynamicNumber length(DynamicNumber x, DynamicNumber y, DynamicNumber z) {
        return lengthSquared(x, y, z).sqrt();
    }

    public static DynamicNumber horizontalDistanceSquared(DynamicNumber x, DynamicNumber z) {
        return square(x).add(square(z));
    }

    public static DynamicNumber horizontalDistance(DynamicNumber x, DynamicNumber z) {
        return horizontalDistanceSquared(x, z).sqrt();
    }

    public static DynamicNumber distanceSquared(DynamicNumber x1, DynamicNumber y1, DynamicNumber z1,
            DynamicNumber x2, DynamicNumber y2, DynamicNumber z2) {
        DynamicNumber dx = x1.subtract(x2);
        DynamicNumber dy = y1.subtract(y2);
        DynamicNumber dz = z1.subtract(z2);
        return square(dx).add(square(dy)).add(square(dz));
    }

    public static DynamicNumber distance(DynamicNumber x1, DynamicNumber y1, DynamicNumber z1,
            DynamicNumber x2, DynamicNumber y2, DynamicNumber z2) {
        return distanceSquared(x1, y1, z1, x2, y2, z2).sqrt();
    }

    // ---------- 快速幂/根 ----------
    public static DynamicNumber fastInvSqrt(DynamicNumber x) {
        double d = x.doubleValue();
        double half = 0.5 * d;
        long i = Double.doubleToRawLongBits(d);
        i = 6910469410427058090L - (i >> 1);
        double result = Double.longBitsToDouble(i);
        result = result * (1.5 - half * result * result);
        return DynamicNumber.of(result);
    }

    // ---------- 其他工具 ----------
    public static int sign(DynamicNumber x) {
        return x.compareTo(DynamicNumber.ZERO) > 0 ? 1 : (x.compareTo(DynamicNumber.ZERO) < 0 ? -1 : 0);
    }

    public static boolean equal(DynamicNumber a, DynamicNumber b) {
        return a.subtract(b).abs().compareTo(EPSILON) < 0;
    }

    public static DynamicNumber max(DynamicNumber a, DynamicNumber b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static DynamicNumber min(DynamicNumber a, DynamicNumber b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static DynamicNumber clampMap(DynamicNumber value, DynamicNumber fromMin, DynamicNumber fromMax,
            DynamicNumber toMin, DynamicNumber toMax) {
        return clampedLerp(inverseLerp(value, fromMin, fromMax), toMin, toMax);
    }

    // ==================== 随机工具（基于 RandomSource） ====================

    /** 生成 [0, bound) 范围内的随机整数 (DynamicNumber) */
    public static DynamicNumber randomBigInt(RandomSource random, DynamicNumber bound) {
        if (bound.compareTo(DynamicNumber.ZERO) <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        // 如果 bound <= Long.MAX_VALUE，直接使用 nextLong
        if (bound.compareTo(DynamicNumber.of(Long.MAX_VALUE)) <= 0) {
            long boundLong = bound.longValue();
            if (boundLong == 0) return DynamicNumber.ZERO;
            // 使用 nextLong 处理边界（推荐使用 nextLong(bound) 但 RandomSource 没有，用 nextInt 模拟）
            if (boundLong <= Integer.MAX_VALUE) {
                return DynamicNumber.of(random.nextInt((int) boundLong));
            } else {
                // 使用 nextLong 并取模（可能略微不均匀，但可接受）
                long value = random.nextLong();
                long abs = value == Long.MIN_VALUE ? 0 : Math.abs(value);
                return DynamicNumber.of(abs % boundLong);
            }
        } else {
            // 超大范围：使用 BigInteger 随机生成（需要额外实现）
            // 简单方案：先用 double 近似，但不够精确
            // 此处提供一种基于 BigInteger 的生成方法（利用 RandomSource 多次调用）
            BigInteger bigBound = bound.bigIntegerValue();
            if (bigBound.signum() <= 0) return DynamicNumber.ZERO;
            // 生成足够多的随机位
            int bitLength = bigBound.bitLength();
            int byteLength = (bitLength + 7) / 8;
            byte[] bytes = new byte[byteLength];
            // 使用 RandomSource 填充字节
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) random.nextInt(256);
            }
            BigInteger randomBig = new BigInteger(1, bytes); // 正数
            // 取模
            BigInteger result = randomBig.mod(bigBound);
            return DynamicNumber.of(result);
        }
    }

    /** 生成 [0, bound) 范围内的随机长整数 (DynamicNumber) - 同 randomBigInt */
    public static DynamicNumber randomBigLong(RandomSource random, DynamicNumber bound) {
        return randomBigInt(random, bound);
    }

    /** 生成 [min, max) 范围内的随机浮点数 (DynamicNumber) */
    public static DynamicNumber randomBigDouble(RandomSource random, DynamicNumber min, DynamicNumber max) {
        if (min.compareTo(max) >= 0) {
            throw new IllegalArgumentException("min >= max");
        }
        double diff = max.subtract(min).doubleValue();
        return min.add(DynamicNumber.of(random.nextDouble() * diff));
    }

    /** 生成 [min, max) 范围内的随机浮点数 (DynamicNumber) */
    public static DynamicNumber randomBigFloat(RandomSource random, DynamicNumber min, DynamicNumber max) {
        if (min.compareTo(max) >= 0) {
            throw new IllegalArgumentException("min >= max");
        }
        double diff = max.subtract(min).doubleValue();
        return min.add(DynamicNumber.of(random.nextFloat() * diff));
    }

    /** 生成高斯（正态）随机数，均值 mean，标准差 deviation */
    public static DynamicNumber randomGaussian(RandomSource random, DynamicNumber mean, DynamicNumber deviation) {
        return mean.add(deviation.multiply(DynamicNumber.of(random.nextGaussian())));
    }

    /** 生成三角分布随机数 (mean ± spread) */
    public static DynamicNumber randomTriangle(RandomSource random, DynamicNumber mean, DynamicNumber spread) {
        return mean.add(spread.multiply(DynamicNumber.of(random.nextDouble() - random.nextDouble())));
    }

    /** 生成 [0, 1) 范围的随机浮点数 (DynamicNumber) */
    public static DynamicNumber randomDouble(RandomSource random) {
        return DynamicNumber.of(random.nextDouble());
    }

    /** 生成 [0, 1) 范围的随机浮点数 (DynamicNumber) */
    public static DynamicNumber randomFloat(RandomSource random) {
        return DynamicNumber.of(random.nextFloat());
    }

    /** 生成随机布尔值 */
    public static DynamicNumber randomBoolean(RandomSource random) {
        return DynamicNumber.of(random.nextBoolean() ? 1 : 0);
    }

    // ---------- 私有构造 ----------
    private BigMath() {}
}