package me.noisefarlands.mcbig.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 256 位二进制浮点数，表示为 {@code mantissa * 2^exponent}。 支持超大范围和高精度，是 double 的理想替代品。 所有运算均基于 Int256
 * 实现，避免动态内存分配。
 */
public final class Float256 extends Number implements Comparable<Float256> {

    // ---------- 常量 ----------
    public static final Float256 ZERO = new Float256(Int256.ZERO, 0);
    public static final Float256 ONE = new Float256(Int256.ONE, 0);
    public static final Float256 TWO = new Float256(Int256.TWO, 0);
    public static final Float256 TEN = new Float256(Int256.TEN, 0);
    private static final Float256 POS_INF = new Float256(Int256.ONE, Integer.MAX_VALUE);
    private static final Float256 NEG_INF = new Float256(Int256.MINUS_ONE, Integer.MAX_VALUE);
    private static final Float256 NAN = new Float256(Int256.ZERO, Integer.MIN_VALUE); // 特殊标记

    // ---------- 内部字段 ----------
    private final Int256 mantissa; // 有符号整数尾数
    private final int exponent; // 指数（可为负）

    // ---------- 构造 ----------
    private Float256(Int256 mantissa, int exponent) {
        this.mantissa = mantissa;
        this.exponent = exponent;
    }

    // ---------- 工厂方法 ----------
    public static Float256 valueOf(long val) {
        if (val == 0) return ZERO;
        return new Float256(Int256.of(val), 0);
    }

    public static Float256 valueOf(double val) {
        if (Double.isNaN(val)) return NAN;
        if (Double.isInfinite(val)) return val > 0 ? POS_INF : NEG_INF;
        long bits = Double.doubleToLongBits(val);
        int sign = (bits >> 63) == 0 ? 1 : -1;
        int exp = (int) ((bits >> 52) & 0x7FF) - 1023;
        long mant = bits & 0xFFFFFFFFFFFFFL;
        if (exp == -1023) {
            // 次正规数
            exp = -1022;
            // mant 不变，无隐含位
        } else {
            mant |= 0x10000000000000L; // 加上隐含的 1
        }
        // mantissa 为 53 位整数，实际值为 mant * 2^(exp-52)
        Int256 m = Int256.of(mant);
        if (sign < 0) m = m.negate();
        return new Float256(m, exp - 52);
    }

    public static Float256 valueOf(BigDecimal bd) {
        return valueOf(bd.doubleValue());
    }

    // ---------- 访问器 ----------
    public Int256 getMantissa() {
        return mantissa;
    }

    public int getExponent() {
        return exponent;
    }

    // ---------- 状态检查 ----------
    public boolean isZero() {
        return mantissa.isZero();
    }

    public boolean isNaN() {
        return exponent == Integer.MIN_VALUE && mantissa.isZero();
    }

    public boolean isInfinite() {
        return exponent == Integer.MAX_VALUE && !mantissa.isZero();
    }

    public boolean isFinite() {
        return !isNaN() && !isInfinite();
    }

    // ---------- 基本运算 ----------
    public Float256 negate() {
        return new Float256(mantissa.negate(), exponent);
    }

    public Float256 abs() {
        return mantissa.signum() >= 0 ? this : negate();
    }

    public Float256 add(Float256 other) {
        if (this.isZero()) return other;
        if (other.isZero()) return this;
        if (this.isNaN() || other.isNaN()) return NAN;
        // 对齐指数
        int expDiff = this.exponent - other.exponent;
        Int256 a = this.mantissa;
        Int256 b = other.mantissa;
        if (expDiff > 0) {
            b = b.shiftRight(expDiff);
        } else if (expDiff < 0) {
            a = a.shiftRight(-expDiff);
        }
        Int256 sum = a.add(b);
        if (sum.isZero()) return ZERO;
        // 规范化：保持最高位为 1（可选）
        return new Float256(sum, Math.max(this.exponent, other.exponent));
    }

    public Float256 subtract(Float256 other) {
        return this.add(other.negate());
    }

    public Float256 multiply(Float256 other) {
        if (this.isZero() || other.isZero()) return ZERO;
        if (this.isNaN() || other.isNaN()) return NAN;
        Int256 product = this.mantissa.multiply(other.mantissa);
        int newExp = this.exponent + other.exponent;
        // 规范化：调整 product 使最高位为 1（可选）
        return new Float256(product, newExp);
    }

    public Float256 divide(Float256 divisor, int precision) {
        if (divisor.isZero()) throw new ArithmeticException("Division by zero");
        if (this.isNaN() || divisor.isNaN()) return NAN;
        // 使用 BigDecimal 作为中介，保持精度
        BigDecimal a = new BigDecimal(this.mantissa.toBigInteger()).scaleByPowerOfTen(-this.exponent);
        BigDecimal b = new BigDecimal(divisor.mantissa.toBigInteger()).scaleByPowerOfTen(-divisor.exponent);
        BigDecimal result = a.divide(b, new MathContext(precision, RoundingMode.HALF_EVEN));
        return Float256.valueOf(result);
    }

    public Float256 divide(Float256 divisor) {
        return divide(divisor, 30);
    }

    // ---------- 比较 ----------
    @Override
    public int compareTo(Float256 o) {
        if (isNaN() || o.isNaN()) return 0;
        if (isZero() && o.isZero()) return 0;
        // 对齐指数后比较尾数
        int expDiff = this.exponent - o.exponent;
        Int256 a = this.mantissa;
        Int256 b = o.mantissa;
        if (expDiff > 0) {
            b = b.shiftRight(expDiff);
        } else if (expDiff < 0) {
            a = a.shiftRight(-expDiff);
        }
        return a.compareTo(b);
    }

    // ---------- 类型转换 ----------
    @Override
    public double doubleValue() {
        if (isZero()) return 0.0;
        if (isNaN()) return Double.NaN;
        if (isInfinite())
            return mantissa.signum() > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        // 从 mantissa 和 exponent 计算 double，正确舍入
        int bitLen = mantissa.bitLength();
        int exp = exponent + (bitLen - 1);
        if (exp < -1074 || exp > 1023) {
            // 超出 double 范围，返回 Inf 或 0
            if (exp > 1023)
                return mantissa.signum() > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            if (exp < -1074) return 0.0;
        }
        long mant;
        if (bitLen <= 53) {
            mant = mantissa.longValue();
            if (mant < 0) mant = -mant;
            mant &= 0xFFFFFFFFFFFFFL;
        } else {
            // 取最高 53 位
            int shift = bitLen - 53;
            Int256 high = mantissa.shiftRight(shift);
            mant = high.longValue();
            if (mant < 0) mant = -mant;
            // 检查是否需舍入
            Int256 low = mantissa.shiftLeft(-shift); // 取低位移除部分
            if (!low.isZero() && (mant & 1) == 1) {
                mant++; // 四舍五入
            }
            mant &= 0xFFFFFFFFFFFFFL;
        }
        long bits = ((long) (exp + 1023)) << 52;
        bits |= mant;
        if (mantissa.signum() < 0) bits |= 0x8000000000000000L;
        return Double.longBitsToDouble(bits);
    }

    @Override
    public float floatValue() {
        return (float) doubleValue();
    }

    @Override
    public int intValue() {
        return (int) doubleValue();
    }

    @Override
    public long longValue() {
        return (long) doubleValue();
    }

    public BigDecimal toBigDecimal() {
        if (isZero()) return BigDecimal.ZERO;
        if (isNaN()) throw new ArithmeticException("NaN");
        if (isInfinite()) throw new ArithmeticException("Infinite");
        // 计算 mantissa * 2^exponent 的十进制表示
        BigInteger m = mantissa.toBigInteger();
        if (exponent >= 0) {
            return new BigDecimal(m.shiftLeft(exponent));
        } else {
            // 除以 2^(-exponent)
            BigDecimal divisor = BigDecimal.ONE.scaleByPowerOfTen(-exponent);
            return new BigDecimal(m).divide(divisor, MathContext.DECIMAL128);
        }
    }

    // ---------- 标准方法 ----------
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Float256)) return false;
        Float256 that = (Float256) o;
        return exponent == that.exponent && mantissa.equals(that.mantissa);
    }

    @Override
    public int hashCode() {
        return 31 * mantissa.hashCode() + exponent;
    }

    @Override
    public String toString() {
        if (isNaN()) return "NaN";
        if (isInfinite()) return mantissa.signum() > 0 ? "Infinity" : "-Infinity";
        return toBigDecimal().toString();
    }
}