package me.noisefarlands.mcbig.util;

import java.math.BigInteger;

public final class DynamicNumber extends Number implements Comparable<DynamicNumber> {

    // ---------- 常量 ----------
    public static final DynamicNumber ZERO = new DynamicNumber(Int256.ZERO, NumberType.INT256);
    public static final DynamicNumber ONE = new DynamicNumber(Int256.ONE, NumberType.INT256);
    public static final DynamicNumber TWO = new DynamicNumber(Int256.TWO, NumberType.INT256);
    public static final DynamicNumber TEN = new DynamicNumber(Int256.TEN, NumberType.INT256);

    private final Number value;
    private final NumberType type;

    // ---------- 构造 ----------
    private DynamicNumber(Number value, NumberType type) {
        this.value = value;
        this.type = type;
    }

    // ---------- 工厂方法 ----------
    public static DynamicNumber of(long val) {
        NumberType type = McBigConfig.getNumberType();
        return switch (type) {
            case LONG -> new DynamicNumber(val, NumberType.LONG);
            case INT256 -> new DynamicNumber(Int256.of(val), NumberType.INT256);
            case BIG_INTEGER -> new DynamicNumber(BigInteger.valueOf(val), NumberType.BIG_INTEGER);
        };
    }

    public static DynamicNumber of(Int256 val) {
        return new DynamicNumber(val, NumberType.INT256);
    }

    public static DynamicNumber of(BigInteger val) {
        return new DynamicNumber(val, NumberType.BIG_INTEGER);
    }

    public static DynamicNumber of(double val) {
        // 将 double 转换为最接近的 long（会损失精度，但兼容性最好）
        return of((long) val);
    }

    public static DynamicNumber of(float val) {
        return of((long) val);
    }
    
    // 在 DynamicNumber 中添加：
public DynamicNumber max(DynamicNumber other) {
    return this.compareTo(other) >= 0 ? this : other;
}

public DynamicNumber min(DynamicNumber other) {
    return this.compareTo(other) <= 0 ? this : other;
}

    // ---------- 算术运算 ----------
    public DynamicNumber add(DynamicNumber other) {
        if (this.type == NumberType.LONG && other.type == NumberType.LONG) {
            long sum = this.longValue() + other.longValue();
            return DynamicNumber.of(sum);
        }
        Int256 a = this.toInt256();
        Int256 b = other.toInt256();
        return DynamicNumber.of(a.add(b));
    }

    public DynamicNumber subtract(DynamicNumber other) {
        if (this.type == NumberType.LONG && other.type == NumberType.LONG) {
            long diff = this.longValue() - other.longValue();
            return DynamicNumber.of(diff);
        }
        Int256 a = this.toInt256();
        Int256 b = other.toInt256();
        return DynamicNumber.of(a.subtract(b));
    }

    public DynamicNumber multiply(DynamicNumber other) {
        Int256 a = this.toInt256();
        Int256 b = other.toInt256();
        return DynamicNumber.of(a.multiply(b));
    }

    public DynamicNumber divide(DynamicNumber other) {
        Int256 a = this.toInt256();
        Int256 b = other.toInt256();
        return DynamicNumber.of(a.divide(b));
    }

    public DynamicNumber negate() {
        return DynamicNumber.of(this.toInt256().negate());
    }

    public DynamicNumber abs() {
        return DynamicNumber.of(this.toInt256().abs());
    }

    public DynamicNumber sqrt() {
        double d = this.doubleValue();
        return DynamicNumber.of((long) Math.sqrt(d));
    }

    public boolean isZero() {
        return this.toInt256().isZero();
    }

    public boolean isFinite() {
        return true; // Int256 始终有限
    }

    // ---------- 比较 ----------
    @Override
    public int compareTo(DynamicNumber o) {
        return this.toInt256().compareTo(o.toInt256());
    }

    // ---------- 类型转换 ----------
    @Override
    public int intValue() {
        return switch (type) {
            case LONG -> (int) value.longValue();
            case INT256 -> ((Int256) value).intValue();
            case BIG_INTEGER -> ((BigInteger) value).intValue();
        };
    }

    @Override
    public long longValue() {
        return switch (type) {
            case LONG -> value.longValue();
            case INT256 -> ((Int256) value).longValue();
            case BIG_INTEGER -> ((BigInteger) value).longValue();
        };
    }

    @Override
    public float floatValue() {
        return (float) doubleValue();
    }

    @Override
    public double doubleValue() {
        return switch (type) {
            case LONG -> value.doubleValue();
            case INT256 -> ((Int256) value).doubleValue();
            case BIG_INTEGER -> ((BigInteger) value).doubleValue();
        };
    }

    public BigInteger bigIntegerValue() {
        return switch (type) {
            case LONG -> BigInteger.valueOf(value.longValue());
            case INT256 -> ((Int256) value).bigIntegerValue();
            case BIG_INTEGER -> (BigInteger) value;
        };
    }

    public Int256 toInt256() {
        return switch (type) {
            case LONG -> Int256.of(value.longValue());
            case INT256 -> (Int256) value;
            case BIG_INTEGER -> new Int256((BigInteger) value);
        };
    }

    public NumberType getType() {
        return type;
    }

    // ---------- 标准方法 ----------
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DynamicNumber)) return false;
        DynamicNumber other = (DynamicNumber) o;
        return this.toInt256().equals(other.toInt256());
    }

    @Override
    public int hashCode() {
        return this.toInt256().hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}