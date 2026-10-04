package me.noisefarlands.mcbig.util;

import java.math.BigInteger;
import java.util.Arrays;

public final class Int256 extends Number implements Comparable<Int256> {
    // ---------- 常量 ----------
    public static final Int256 ZERO = new Int256(0);
    public static final Int256 ONE = new Int256(1);
    public static final Int256 TWO = new Int256(2);
    public static final Int256 TEN = new Int256(10);
    public static final Int256 MINUS_ONE = new Int256(-1);
    public static final Int256 MIN_VALUE = new Int256(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);
    public static final Int256 MAX_VALUE = new Int256(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);

    // ---------- 内部存储 ----------
    private final long high;
    private final long midHigh;
    private final long midLow;
    private final long low;

    // ---------- 构造 ----------
    public Int256(long value) {
        this.low = value;
        this.midLow = (value >> 63) == 0 ? 0 : -1L;
        this.midHigh = this.midLow;
        this.high = this.midLow;
    }

    public Int256(long high, long midHigh, long midLow, long low) {
        this.high = high;
        this.midHigh = midHigh;
        this.midLow = midLow;
        this.low = low;
    }

    public Int256(BigInteger big) {
        if (big.signum() == 0) {
            this.high = 0; this.midHigh = 0; this.midLow = 0; this.low = 0;
            return;
        }
        byte[] bytes = big.toByteArray();
        int len = Math.min(32, bytes.length);
        long h = 0, mh = 0, ml = 0, l = 0;
        int offset = bytes.length - len;
        for (int i = 0; i < len; i++) {
            long b = ((long) bytes[offset + i]) & 0xFFL;
            int shift = (len - 1 - i) * 8;
            if (shift >= 96) h = (h << 8) | b;
            else if (shift >= 64) mh = (mh << 8) | b;
            else if (shift >= 32) ml = (ml << 8) | b;
            else l = (l << 8) | b;
        }
        // 符号扩展
        if (big.signum() < 0 && bytes.length < 32) {
            int remaining = 32 - bytes.length;
            for (int i = 0; i < remaining; i++) {
                int shift = (remaining - 1 - i) * 8;
                if (shift >= 96) h = (h << 8) | 0xFFL;
                else if (shift >= 64) mh = (mh << 8) | 0xFFL;
                else if (shift >= 32) ml = (ml << 8) | 0xFFL;
                else l = (l << 8) | 0xFFL;
            }
        }
        this.high = h;
        this.midHigh = mh;
        this.midLow = ml;
        this.low = l;
    }

    public static Int256 of(long value) { return new Int256(value); }
    public static Int256 of(BigInteger big) { return new Int256(big); }

    // ---------- 核心访问 ----------
    public long getHigh() { return high; }
    public long getMidHigh() { return midHigh; }
    public long getMidLow() { return midLow; }
    public long getLow() { return low; }

    // ---------- 符号 ----------
    public int signum() {
        return (high >>> 63) == 0 ? (isZero() ? 0 : 1) : -1;
    }

    public boolean isZero() {
        return high == 0 && midHigh == 0 && midLow == 0 && low == 0;
    }

    // ---------- 基本运算 ----------
    public Int256 add(Int256 other) {
        long[] result = new long[4];
        long carry = 0;
        for (int i = 3; i >= 0; i--) {
            long a = getPart(i);
            long b = other.getPart(i);
            long sum = a + b + carry;
            result[i] = sum;
            carry = ((a & b) | ((a | b) & ~sum)) >>> 63;
        }
        return new Int256(result[0], result[1], result[2], result[3]);
    }

    public Int256 subtract(Int256 other) {
        return this.add(other.negate());
    }

    public Int256 negate() {
        if (this.equals(MIN_VALUE)) return MIN_VALUE;
        long[] parts = getParts();
        long carry = 1;
        for (int i = 3; i >= 0; i--) {
            long comp = ~parts[i] + carry;
            parts[i] = comp;
            carry = (comp >>> 63) & 1;
        }
        return new Int256(parts[0], parts[1], parts[2], parts[3]);
    }

    public Int256 abs() {
        return signum() >= 0 ? this : negate();
    }

    public Int256 multiply(Int256 other) {
        BigInteger a = this.toBigInteger();
        BigInteger b = other.toBigInteger();
        return new Int256(a.multiply(b));
    }

    public Int256 divide(Int256 divisor) {
        if (divisor.isZero()) throw new ArithmeticException("Division by zero");
        return new Int256(this.toBigInteger().divide(divisor.toBigInteger()));
    }

    public Int256 pow(int exponent) {
        if (exponent < 0) throw new ArithmeticException("Negative exponent");
        if (exponent == 0) return ONE;
        if (this.equals(ONE)) return ONE;
        if (this.equals(ZERO)) return ZERO;
        return new Int256(this.toBigInteger().pow(exponent));
    }

    // ---------- 位移 ----------
    public Int256 shiftLeft(int n) {
        if (n == 0) return this;
        if (n < 0) return shiftRight(-n);
        if (n >= 256) return ZERO;
        int wordShift = n >> 6;
        int bitShift = n & 63;
        long[] parts = getParts();
        long[] result = new long[4];
        for (int i = 0; i < 4 - wordShift; i++) {
            long val = parts[i] << bitShift;
            if (bitShift > 0 && i > 0) val |= parts[i-1] >>> (64 - bitShift);
            result[i + wordShift] = val;
        }
        return new Int256(result[0], result[1], result[2], result[3]);
    }

    public Int256 shiftRight(int n) {
        if (n == 0) return this;
        if (n < 0) return shiftLeft(-n);
        if (n >= 256) return ZERO;
        int wordShift = n >> 6;
        int bitShift = n & 63;
        long[] parts = getParts();
        long[] result = new long[4];
        for (int i = wordShift; i < 4; i++) {
            long val = parts[i] >>> bitShift;
            if (bitShift > 0 && i < 3) val |= parts[i+1] << (64 - bitShift);
            result[i - wordShift] = val;
        }
        return new Int256(result[0], result[1], result[2], result[3]);
    }

    // ---------- 比较 ----------
    @Override
    public int compareTo(Int256 o) {
        if (high != o.high) return Long.compareUnsigned(high, o.high);
        if (midHigh != o.midHigh) return Long.compareUnsigned(midHigh, o.midHigh);
        if (midLow != o.midLow) return Long.compareUnsigned(midLow, o.midLow);
        if (low != o.low) return Long.compareUnsigned(low, o.low);
        return 0;
    }

    // ---------- 类型转换 ----------
    @Override
    public int intValue() { return (int) low; }
    @Override
    public long longValue() { return low; }
    @Override
    public float floatValue() { return (float) doubleValue(); }
    @Override
    public double doubleValue() {
        if (isZero()) return 0.0;
        double val = 0.0;
        val += (low & 0xFFFFFFFFL) * 1.0;
        val += (midLow & 0xFFFFFFFFL) * 4294967296.0;
        val += (midHigh & 0xFFFFFFFFL) * 1.8446744073709552e19;
        val += (high & 0x7FFFFFFFFFFFFFFFL) * 7.922816251426434e28;
        return signum() < 0 ? -val : val;
    }

    public BigInteger toBigInteger() {
        if (isZero()) return BigInteger.ZERO;
        byte[] bytes = new byte[32];
        for (int i = 0; i < 4; i++) {
            long part = getPart(i);
            for (int j = 0; j < 8; j++) {
                bytes[31 - (i * 8 + j)] = (byte) ((part >> (j * 8)) & 0xFF);
            }
        }
        return new BigInteger(bytes);
    }

    public BigInteger bigIntegerValue() {
        return this.toBigInteger();
    }

    // ---------- 工具 ----------
    public int bitLength() {
        if (isZero()) return 0;
        int bits = 0;
        if (high != 0) bits = 64 - Long.numberOfLeadingZeros(high) + 192;
        else if (midHigh != 0) bits = 64 - Long.numberOfLeadingZeros(midHigh) + 128;
        else if (midLow != 0) bits = 64 - Long.numberOfLeadingZeros(midLow) + 64;
        else bits = 64 - Long.numberOfLeadingZeros(low);
        return bits;
    }

    public int getLowestSetBit() {
        if (isZero()) return -1;
        if ((low & 1) != 0) return 0;
        if (low != 0) return Long.numberOfTrailingZeros(low);
        if (midLow != 0) return 64 + Long.numberOfTrailingZeros(midLow);
        if (midHigh != 0) return 128 + Long.numberOfTrailingZeros(midHigh);
        return 192 + Long.numberOfTrailingZeros(high);
    }

    // ---------- 私有辅助 ----------
    private long getPart(int index) {
        return switch (index) {
            case 0 -> high;
            case 1 -> midHigh;
            case 2 -> midLow;
            case 3 -> low;
            default -> throw new IllegalArgumentException();
        };
    }

    private long[] getParts() {
        return new long[]{high, midHigh, midLow, low};
    }

    // ---------- 标准方法 ----------
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Int256 other)) return false;
        return high == other.high && midHigh == other.midHigh && midLow == other.midLow && low == other.low;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new long[]{high, midHigh, midLow, low});
    }

    @Override
    public String toString() {
        return toBigInteger().toString();
    }
}