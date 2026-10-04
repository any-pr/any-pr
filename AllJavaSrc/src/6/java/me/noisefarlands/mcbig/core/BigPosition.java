package me.noisefarlands.mcbig.core;

import me.noisefarlands.mcbig.util.DynamicNumber;
import me.noisefarlands.mcbig.util.NumberType;
import net.minecraft.core.Position;

/**
 * 256bit 坐标接口，统一使用 DynamicNumber 存储坐标值。
 * 所有原版 Position 实现类（BlockPos, Vec3, etc.）将逐步迁移至此接口。
 * 
 * 保留 x()、y()、z() 返回 double 以保持向下兼容。
 */
public interface BigPosition extends Position {

    /**
     * 主要数据接口：返回 DynamicNumber 类型的坐标值
     */
    DynamicNumber bigX();
    DynamicNumber bigY();
    DynamicNumber bigZ();

    // ---------- 向下兼容原版 Position ----------
    @Override
    default double x() {
        return bigX().doubleValue();
    }

    @Override
    default double y() {
        return bigY().doubleValue();
    }

    @Override
    default double z() {
        return bigZ().doubleValue();
    }

    // ---------- 工厂方法 ----------
    static BigPosition of(DynamicNumber x, DynamicNumber y, DynamicNumber z) {
        return new BigPositionImpl(x, y, z);
    }

    static BigPosition of(long x, long y, long z) {
        return new BigPositionImpl(
            DynamicNumber.of(x),
            DynamicNumber.of(y),
            DynamicNumber.of(z)
        );
    }

    static BigPosition of(double x, double y, double z) {
        return new BigPositionImpl(
            DynamicNumber.of((long) x),
            DynamicNumber.of((long) y),
            DynamicNumber.of((long) z)
        );
    }
}

/**
 * 简单实现类，便于测试和过渡
 */
final class BigPositionImpl implements BigPosition {
    private final DynamicNumber x, y, z;

    BigPositionImpl(DynamicNumber x, DynamicNumber y, DynamicNumber z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public DynamicNumber bigX() { return x; }
    @Override
    public DynamicNumber bigY() { return y; }
    @Override
    public DynamicNumber bigZ() { return z; }
}