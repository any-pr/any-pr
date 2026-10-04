package me.noisefarlands.mcbig.Math;

// 改为
import me.noisefarlands.mcbig.util.Int256;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Decimal256 extends Number implements Comparable<Decimal256> {

    // ---------- 常量 ----------
    public static final Decimal256 ZERO = new Decimal256(Int256.ZERO, 0);
    public static final Decimal256 ONE = new Decimal256(Int256.ONE, 0);
    public static final Decimal256 TEN = new Decimal256(Int256.TEN, 0);

    // ---------- 内部字段 ----------
    private final Int256 mantissa;
    private final int scale;  // 小数位数

    // ---------- 构造 ----------
    private Decimal256(Int256 mantissa, int scale) {
        this.mantissa = mantissa;
        this.scale = scale;
    }

    public static Decimal256 of(Int256 mantissa, int scale) {
        return new Decimal256(mantissa, scale);
    }

    public static Decimal256 of(long value) {
        return new Decimal256(Int256.of(value), 0);
    }

    public static Decimal256 of(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new NumberFormatException("Infinite or NaN");
        }
        BigDecimal bd = BigDecimal.valueOf(value);
        Int256 mant = new Int256(bd.unscaledValue());
        return new Decimal256(mant, bd.scale());
    }

    // ---------- 访问器 ----------
    public Int256 getMantissa() { return mantissa; }
    public int getScale() { return scale; }

    // ---------- 算术 ----------
    public Decimal256 add(Decimal256 other) {
        Int256 a = this.mantissa;
        Int256 b = other.mantissa;
        int sa = this.scale;
        int sb = other.scale;
        int maxScale = Math.max(sa, sb);
        if (sa < maxScale) a = a.multiply(Int256.TEN.pow(maxScale - sa));
        if (sb < maxScale) b = b.multiply(Int256.TEN.pow(maxScale - sb));
        return new Decimal256(a.add(b), maxScale);
    }

    public Decimal256 subtract(Decimal256 other) {
        return this.add(other.negate());
    }

    public Decimal256 multiply(Decimal256 other) {
        Int256 product = this.mantissa.multiply(other.mantissa);
        return new Decimal256(product, this.scale + other.scale);
    }

    public Decimal256 divide(Decimal256 divisor, RoundingMode roundingMode) {
        // 使用 BigDecimal 作为中介，精度设为 30
        BigDecimal a = new BigDecimal(this.mantissa.toBigInteger(), this.scale);
        BigDecimal b = new BigDecimal(divisor.mantissa.toBigInteger(), divisor.scale);
        BigDecimal result = a.divide(b, 30, roundingMode);
        return Decimal256.of(result.doubleValue()); // 简化，可优化
    }

    public Decimal256 negate() {
        return new Decimal256(this.mantissa.negate(), this.scale);
    }

    public Decimal256 abs() {
        return new Decimal256(this.mantissa.abs(), this.scale);
    }

    // ---------- 比较 ----------
    @Override
    public int compareTo(Decimal256 o) {
        Int256 a = this.mantissa;
        Int256 b = o.mantissa;
        int sa = this.scale;
        int sb = o.scale;
        int maxScale = Math.max(sa, sb);
        if (sa < maxScale) a = a.multiply(Int256.TEN.pow(maxScale - sa));
        if (sb < maxScale) b = b.multiply(Int256.TEN.pow(maxScale - sb));
        return a.compareTo(b);
    }

    // ---------- 类型转换 ----------
    @Override
    public int intValue() { return (int) doubleValue(); }
    @Override
    public long longValue() { return (long) doubleValue(); }
    @Override
    public float floatValue() { return (float) doubleValue(); }

    @Override
    public double doubleValue() {
        BigDecimal bd = new BigDecimal(this.mantissa.toBigInteger(), this.scale);
        return bd.doubleValue();
    }

    public BigDecimal toBigDecimal() {
        return new BigDecimal(this.mantissa.toBigInteger(), this.scale);
    }

    // ---------- 标准方法 ----------
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Decimal256 that)) return false;
        return this.scale == that.scale && this.mantissa.equals(that.mantissa);
    }

    @Override
    public int hashCode() {
        return 31 * this.mantissa.hashCode() + this.scale;
    }

    @Override
    public String toString() {
        return this.toBigDecimal().toString();
    }
}