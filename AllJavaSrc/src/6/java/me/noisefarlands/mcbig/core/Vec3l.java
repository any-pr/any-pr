package me.noisefarlands.mcbig.core;

import com.google.common.base.MoreObjects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import me.noisefarlands.mcbig.util.DynamicNumber;
import me.noisefarlands.mcbig.util.NumberType;
import me.noisefarlands.mcbig.util.McBigConfig;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Vector3i;

import javax.annotation.concurrent.Immutable;
import java.util.stream.IntStream;

/**
 * 256bit 无限整数坐标向量，基于 {@link DynamicNumber} 存储。
 * 完全兼容原版 Vec3i 的所有方法，但支持远超 int 范围的坐标值。
 * 
 * 命名 l 代表 long / limitless，对应原版 i (int)。
 */
@Immutable
public class Vec3l implements BigPosition, Comparable<Vec3l> {

    // ---------- 常量 ----------
    public static final Vec3l ZERO = new Vec3l(DynamicNumber.ZERO, DynamicNumber.ZERO, DynamicNumber.ZERO);

    // ---------- Codec ----------
    public static final Codec<Vec3l> CODEC = Codec.INT_STREAM
        .comapFlatMap(
            input -> Util.fixedSize(input, 3).map(ints -> new Vec3l(ints[0], ints[1], ints[2])),
            pos -> IntStream.of((int) pos.getX(), (int) pos.getY(), (int) pos.getZ())
        );

    public static final StreamCodec<ByteBuf, Vec3l> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, v -> (int) v.getX(),
        ByteBufCodecs.VAR_INT, v -> (int) v.getY(),
        ByteBufCodecs.VAR_INT, v -> (int) v.getZ(),
        Vec3l::new
    );

    // ---------- 内部字段 ----------
    private final DynamicNumber x;
    private final DynamicNumber y;
    private final DynamicNumber z;

    // ---------- 构造 ----------
    public Vec3l(DynamicNumber x, DynamicNumber y, DynamicNumber z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3l(long x, long y, long z) {
        this(DynamicNumber.of(x), DynamicNumber.of(y), DynamicNumber.of(z));
    }

    public Vec3l(int x, int y, int z) {
        this((long) x, (long) y, (long) z);
    }

    public Vec3l(Vec3i vec) {
        this(vec.getX(), vec.getY(), vec.getZ());
    }

    // ---------- 工厂 ----------
    public static Vec3l of(DynamicNumber x, DynamicNumber y, DynamicNumber z) {
        return new Vec3l(x, y, z);
    }

    public static Vec3l of(long x, long y, long z) {
        return new Vec3l(x, y, z);
    }

    public static Vec3l of(Vec3i vec) {
        return new Vec3l(vec);
    }

    // ---------- 实现 BigPosition ----------
    @Override
    public DynamicNumber bigX() { return x; }

    @Override
    public DynamicNumber bigY() { return y; }

    @Override
    public DynamicNumber bigZ() { return z; }

    // ---------- 原版兼容访问器 ----------
    public long getX() { return x.longValue(); }
    public long getY() { return y.longValue(); }
    public long getZ() { return z.longValue(); }

    // ---------- 内部 setter（protected） ----------
    protected Vec3l setX(DynamicNumber x) {
        return new Vec3l(x, this.y, this.z);
    }
    protected Vec3l setY(DynamicNumber y) {
        return new Vec3l(this.x, y, this.z);
    }
    protected Vec3l setZ(DynamicNumber z) {
        return new Vec3l(this.x, this.y, z);
    }

    // ---------- 算术运算 ----------
    public Vec3l offset(DynamicNumber dx, DynamicNumber dy, DynamicNumber dz) {
        if (dx.isZero() && dy.isZero() && dz.isZero()) return this;
        return new Vec3l(x.add(dx), y.add(dy), z.add(dz));
    }

    public Vec3l offset(long dx, long dy, long dz) {
        return offset(DynamicNumber.of(dx), DynamicNumber.of(dy), DynamicNumber.of(dz));
    }

    public Vec3l offset(Vec3l vec) {
        return offset(vec.x, vec.y, vec.z);
    }

    public Vec3l subtract(Vec3l vec) {
        return offset(vec.x.negate(), vec.y.negate(), vec.z.negate());
    }

    public Vec3l multiply(DynamicNumber scale) {
        if (scale.equals(DynamicNumber.ONE)) return this;
        if (scale.isZero()) return ZERO;
        return new Vec3l(x.multiply(scale), y.multiply(scale), z.multiply(scale));
    }

    public Vec3l multiply(long scale) {
        return multiply(DynamicNumber.of(scale));
    }

    public Vec3l multiply(DynamicNumber xScale, DynamicNumber yScale, DynamicNumber zScale) {
        return new Vec3l(x.multiply(xScale), y.multiply(yScale), z.multiply(zScale));
    }

    // ---------- 方向辅助 ----------
    public Vec3l above() { return above(1); }
    public Vec3l above(long steps) { return relative(Direction.UP, steps); }

    public Vec3l below() { return below(1); }
    public Vec3l below(long steps) { return relative(Direction.DOWN, steps); }

    public Vec3l north() { return north(1); }
    public Vec3l north(long steps) { return relative(Direction.NORTH, steps); }

    public Vec3l south() { return south(1); }
    public Vec3l south(long steps) { return relative(Direction.SOUTH, steps); }

    public Vec3l west() { return west(1); }
    public Vec3l west(long steps) { return relative(Direction.WEST, steps); }

    public Vec3l east() { return east(1); }
    public Vec3l east(long steps) { return relative(Direction.EAST, steps); }

    public Vec3l relative(Direction direction) { return relative(direction, 1); }
    public Vec3l relative(Direction direction, long steps) {
        if (steps == 0) return this;
        DynamicNumber dx = DynamicNumber.of(direction.getStepX() * steps);
        DynamicNumber dy = DynamicNumber.of(direction.getStepY() * steps);
        DynamicNumber dz = DynamicNumber.of(direction.getStepZ() * steps);
        return new Vec3l(x.add(dx), y.add(dy), z.add(dz));
    }

    public Vec3l relative(Direction.Axis axis, long steps) {
        if (steps == 0) return this;
        DynamicNumber dx = axis == Direction.Axis.X ? DynamicNumber.of(steps) : DynamicNumber.ZERO;
        DynamicNumber dy = axis == Direction.Axis.Y ? DynamicNumber.of(steps) : DynamicNumber.ZERO;
        DynamicNumber dz = axis == Direction.Axis.Z ? DynamicNumber.of(steps) : DynamicNumber.ZERO;
        return new Vec3l(x.add(dx), y.add(dy), z.add(dz));
    }

    // ---------- 叉积 ----------
    public Vec3l cross(Vec3l other) {
        return new Vec3l(
            y.multiply(other.z).subtract(z.multiply(other.y)),
            z.multiply(other.x).subtract(x.multiply(other.z)),
            x.multiply(other.y).subtract(y.multiply(other.x))
        );
    }

    // ---------- 距离计算 ----------
    public DynamicNumber distSqr(Vec3l other) {
        DynamicNumber dx = x.subtract(other.x);
        DynamicNumber dy = y.subtract(other.y);
        DynamicNumber dz = z.subtract(other.z);
        return dx.multiply(dx).add(dy.multiply(dy)).add(dz.multiply(dz));
    }

    public DynamicNumber distToCenterSqr(Position pos) {
        return distToCenterSqr(DynamicNumber.of(pos.x()), DynamicNumber.of(pos.y()), DynamicNumber.of(pos.z()));
    }

    public DynamicNumber distToCenterSqr(DynamicNumber px, DynamicNumber py, DynamicNumber pz) {
        DynamicNumber cx = x.add(DynamicNumber.of(0.5)).subtract(px);
        DynamicNumber cy = y.add(DynamicNumber.of(0.5)).subtract(py);
        DynamicNumber cz = z.add(DynamicNumber.of(0.5)).subtract(pz);
        return cx.multiply(cx).add(cy.multiply(cy)).add(cz.multiply(cz));
    }

    public DynamicNumber distToLowCornerSqr(DynamicNumber px, DynamicNumber py, DynamicNumber pz) {
        DynamicNumber dx = x.subtract(px);
        DynamicNumber dy = y.subtract(py);
        DynamicNumber dz = z.subtract(pz);
        return dx.multiply(dx).add(dy.multiply(dy)).add(dz.multiply(dz));
    }

    public DynamicNumber distManhattan(Vec3l other) {
        DynamicNumber dx = x.subtract(other.x).abs();
        DynamicNumber dy = y.subtract(other.y).abs();
        DynamicNumber dz = z.subtract(other.z).abs();
        return dx.add(dy).add(dz);
    }

    public DynamicNumber distChessboard(Vec3l other) {
        DynamicNumber dx = x.subtract(other.x).abs();
        DynamicNumber dy = y.subtract(other.y).abs();
        DynamicNumber dz = z.subtract(other.z).abs();
        return dx.max(dy).max(dz);
    }

    public boolean closerThan(Vec3l pos, double distance) {
        return distSqr(pos).doubleValue() < Mth.square(distance);
    }

    // ---------- 坐标提取 ----------
    public long get(Direction.Axis axis) {
        return switch (axis) {
            case X -> x.longValue();
            case Y -> y.longValue();
            case Z -> z.longValue();
        };
    }

    // ---------- 转换 ----------
    public Vector3i toMutable() {
        return new Vector3i((int) x.longValue(), (int) y.longValue(), (int) z.longValue());
    }

    public Vec3i toVec3i() {
        return new Vec3i((int) x.longValue(), (int) y.longValue(), (int) z.longValue());
    }

    // ---------- Comparable ----------
    @Override
    public int compareTo(Vec3l o) {
        int yComp = y.compareTo(o.y);
        if (yComp != 0) return yComp;
        int zComp = z.compareTo(o.z);
        if (zComp != 0) return zComp;
        return x.compareTo(o.x);
    }

    // ---------- 标准方法 ----------
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vec3l other)) return false;
        return x.equals(other.x) && y.equals(other.y) && z.equals(other.z);
    }

    @Override
    public int hashCode() {
        int result = x.hashCode();
        result = 31 * result + y.hashCode();
        result = 31 * result + z.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("x", x.longValue())
            .add("y", y.longValue())
            .add("z", z.longValue())
            .toString();
    }

    public String toShortString() {
        return x.longValue() + ", " + y.longValue() + ", " + z.longValue();
    }

    // ---------- 原版 Position 兼容 ----------
    @Override
    public double x() { return x.doubleValue(); }
    @Override
    public double y() { return y.doubleValue(); }
    @Override
    public double z() { return z.doubleValue(); }
}