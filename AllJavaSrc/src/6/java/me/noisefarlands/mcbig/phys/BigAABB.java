package me.noisefarlands.mcbig.phys;

import me.noisefarlands.mcbig.Math.BigMath;
import me.noisefarlands.mcbig.core.BigVec3;
import me.noisefarlands.mcbig.core.Vec3l;
import me.noisefarlands.mcbig.util.DynamicNumber;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import org.joml.Vector3f;

/**
 * 256bit 无限世界轴对齐包围盒，基于 BigVec3 存储坐标。 所有方法均使用 BigVec3 和 DynamicNumber，支持超大范围坐标运算。
 *
 * <p>提供与原版 AABB 的互转，便于逐步迁移。
 */
public class BigAABB {

    // ---------- 内部字段 ----------
    private final BigVec3 minPos;
    private final BigVec3 maxPos;

    // ---------- 构造 ----------
    public BigAABB(BigVec3 minPos, BigVec3 maxPos) {
        // 确保 min <= max
        DynamicNumber minX = BigMath.min(minPos.bigX(), maxPos.bigX());
        DynamicNumber minY = BigMath.min(minPos.bigY(), maxPos.bigY());
        DynamicNumber minZ = BigMath.min(minPos.bigZ(), maxPos.bigZ());
        DynamicNumber maxX = BigMath.max(minPos.bigX(), maxPos.bigX());
        DynamicNumber maxY = BigMath.max(minPos.bigY(), maxPos.bigY());
        DynamicNumber maxZ = BigMath.max(minPos.bigZ(), maxPos.bigZ());
        this.minPos = new BigVec3(minX, minY, minZ);
        this.maxPos = new BigVec3(maxX, maxY, maxZ);
    }

    public BigAABB(DynamicNumber minX, DynamicNumber minY, DynamicNumber minZ,
            DynamicNumber maxX, DynamicNumber maxY, DynamicNumber maxZ) {
        this(new BigVec3(minX, minY, minZ), new BigVec3(maxX, maxY, maxZ));
    }

    public BigAABB(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        this(DynamicNumber.of(minX), DynamicNumber.of(minY), DynamicNumber.of(minZ),
        DynamicNumber.of(maxX), DynamicNumber.of(maxY), DynamicNumber.of(maxZ));
    }

    public BigAABB(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    public BigAABB(Vec3 begin, Vec3 end) {
        this(begin.x, begin.y, begin.z, end.x, end.y, end.z);
    }

    public BigAABB(AABB aabb) {
        this(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    // ---------- 工厂 ----------
    public static BigAABB of(BoundingBox box) {
        return new BigAABB(box.minX(), box.minY(), box.minZ(),
        box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
    }

    public static BigAABB unitCubeFromLowerCorner(BigVec3 pos) {
        return new BigAABB(pos, pos.add(1, 1, 1));
    }

    public static BigAABB encapsulatingFullBlocks(Vec3l pos0, Vec3l pos1) {
        return new BigAABB(
        BigMath.min(pos0.bigX(), pos1.bigX()),
        BigMath.min(pos0.bigY(), pos1.bigY()),
        BigMath.min(pos0.bigZ(), pos1.bigZ()),
        BigMath.max(pos0.bigX(), pos1.bigX()).add(DynamicNumber.ONE),
        BigMath.max(pos0.bigY(), pos1.bigY()).add(DynamicNumber.ONE),
        BigMath.max(pos0.bigZ(), pos1.bigZ()).add(DynamicNumber.ONE)
        );
    }

    // ---------- 访问器 ----------
    public BigVec3 getMinPos() {
        return minPos;
    }

    public BigVec3 getMaxPos() {
        return maxPos;
    }

    public DynamicNumber getMinX() {
        return minPos.bigX();
    }

    public DynamicNumber getMinY() {
        return minPos.bigY();
    }

    public DynamicNumber getMinZ() {
        return minPos.bigZ();
    }

    public DynamicNumber getMaxX() {
        return maxPos.bigX();
    }

    public DynamicNumber getMaxY() {
        return maxPos.bigY();
    }

    public DynamicNumber getMaxZ() {
        return maxPos.bigZ();
    }

    public DynamicNumber getXsize() {
        return maxPos.bigX().subtract(minPos.bigX());
    }

    public DynamicNumber getYsize() {
        return maxPos.bigY().subtract(minPos.bigY());
    }

    public DynamicNumber getZsize() {
        return maxPos.bigZ().subtract(minPos.bigZ());
    }

    public DynamicNumber getSize() {
        DynamicNumber xs = getXsize();
        DynamicNumber ys = getYsize();
        DynamicNumber zs = getZsize();
        return xs.add(ys).add(zs).divide(DynamicNumber.of(3));
    }

    // ---------- 修改操作（返回新实例） ----------
    // ---------- 修改最小值 ----------
    public BigAABB setMinX(DynamicNumber newMinX) {
        return new BigAABB(newMinX, minPos.bigY(), minPos.bigZ(),
        maxPos.bigX(), maxPos.bigY(), maxPos.bigZ());
    }

    public BigAABB setMinY(DynamicNumber newMinY) {
        return new BigAABB(minPos.bigX(), newMinY, minPos.bigZ(),
        maxPos.bigX(), maxPos.bigY(), maxPos.bigZ());
    }

    public BigAABB setMinZ(DynamicNumber newMinZ) {
        return new BigAABB(minPos.bigX(), minPos.bigY(), newMinZ,
        maxPos.bigX(), maxPos.bigY(), maxPos.bigZ());
    }

// ---------- 修改最大值 ----------
    public BigAABB setMaxX(DynamicNumber newMaxX) {
        return new BigAABB(minPos.bigX(), minPos.bigY(), minPos.bigZ(),
        newMaxX, maxPos.bigY(), maxPos.bigZ());
    }

    public BigAABB setMaxY(DynamicNumber newMaxY) {
        return new BigAABB(minPos.bigX(), minPos.bigY(), minPos.bigZ(),
        maxPos.bigX(), newMaxY, maxPos.bigZ());
    }

    public BigAABB setMaxZ(DynamicNumber newMaxZ) {
        return new BigAABB(minPos.bigX(), minPos.bigY(), minPos.bigZ(),
        maxPos.bigX(), maxPos.bigY(), newMaxZ);
    }

    // ---------- 移动 ----------
    public BigAABB move(DynamicNumber dx, DynamicNumber dy, DynamicNumber dz) {
        return new BigAABB(
        minPos.bigX().add(dx), minPos.bigY().add(dy), minPos.bigZ().add(dz),
        maxPos.bigX().add(dx), maxPos.bigY().add(dy), maxPos.bigZ().add(dz)
        );
    }

    public BigAABB move(BigVec3 delta) {
        return move(delta.bigX(), delta.bigY(), delta.bigZ());
    }

    public BigAABB move(Vec3l delta) {
        return move(delta.bigX(), delta.bigY(), delta.bigZ());
    }

    public BigAABB move(Vec3 delta) {
        return move(DynamicNumber.of(delta.x), DynamicNumber.of(delta.y), DynamicNumber.of(delta.z));
    }

    public BigAABB move(BlockPos pos) {
        return move(DynamicNumber.of(pos.getX()), DynamicNumber.of(pos.getY()), DynamicNumber.of(pos.getZ()));
    }

    // ---------- 伸缩（膨胀/收缩） ----------
    public BigAABB inflate(DynamicNumber xAdd, DynamicNumber yAdd, DynamicNumber zAdd) {
        return new BigAABB(
        minPos.bigX().subtract(xAdd),
        minPos.bigY().subtract(yAdd),
        minPos.bigZ().subtract(zAdd),
        maxPos.bigX().add(xAdd),
        maxPos.bigY().add(yAdd),
        maxPos.bigZ().add(zAdd)
        );
    }

    public BigAABB inflate(DynamicNumber amount) {
        return inflate(amount, amount, amount);
    }

    public BigAABB inflate(double amount) {
        return inflate(DynamicNumber.of(amount));
    }

    public BigAABB deflate(DynamicNumber amount) {
        return inflate(amount.negate());
    }

    // ---------- 扩张（沿特定方向） ----------
    public BigAABB expandTowards(DynamicNumber dx, DynamicNumber dy, DynamicNumber dz) {
        DynamicNumber newMinX = minPos.bigX();
        DynamicNumber newMinY = minPos.bigY();
        DynamicNumber newMinZ = minPos.bigZ();
        DynamicNumber newMaxX = maxPos.bigX();
        DynamicNumber newMaxY = maxPos.bigY();
        DynamicNumber newMaxZ = maxPos.bigZ();

        if (dx.compareTo(DynamicNumber.ZERO) < 0) newMinX = newMinX.add(dx);
        else if (dx.compareTo(DynamicNumber.ZERO) > 0) newMaxX = newMaxX.add(dx);

        if (dy.compareTo(DynamicNumber.ZERO) < 0) newMinY = newMinY.add(dy);
        else if (dy.compareTo(DynamicNumber.ZERO) > 0) newMaxY = newMaxY.add(dy);

        if (dz.compareTo(DynamicNumber.ZERO) < 0) newMinZ = newMinZ.add(dz);
        else if (dz.compareTo(DynamicNumber.ZERO) > 0) newMaxZ = newMaxZ.add(dz);

        return new BigAABB(newMinX, newMinY, newMinZ, newMaxX, newMaxY, newMaxZ);
    }

    public BigAABB expandTowards(BigVec3 delta) {
        return expandTowards(delta.bigX(), delta.bigY(), delta.bigZ());
    }

    // ---------- 相交检测 ----------
    public boolean intersects(BigAABB other) {
        return getMinX().compareTo(other.getMaxX()) < 0 &&
                getMaxX().compareTo(other.getMinX()) > 0 &&
                getMinY().compareTo(other.getMaxY()) < 0 &&
                getMaxY().compareTo(other.getMinY()) > 0 &&
                getMinZ().compareTo(other.getMaxZ()) < 0 &&
                getMaxZ().compareTo(other.getMinZ()) > 0;
    }

    public boolean intersects(DynamicNumber minX, DynamicNumber minY, DynamicNumber minZ,
            DynamicNumber maxX, DynamicNumber maxY, DynamicNumber maxZ) {
        return getMinX().compareTo(maxX) < 0 &&
                getMaxX().compareTo(minX) > 0 &&
                getMinY().compareTo(maxY) < 0 &&
                getMaxY().compareTo(minY) > 0 &&
                getMinZ().compareTo(maxZ) < 0 &&
                getMaxZ().compareTo(minZ) > 0;
    }

    public boolean intersects(BigVec3 min, BigVec3 max) {
        return intersects(min.bigX(), min.bigY(), min.bigZ(),
        max.bigX(), max.bigY(), max.bigZ());
    }

    public boolean intersects(BlockPos pos) {
        return intersects(
        DynamicNumber.of(pos.getX()), DynamicNumber.of(pos.getY()), DynamicNumber.of(pos.getZ()),
        DynamicNumber.of(pos.getX() + 1), DynamicNumber.of(pos.getY() + 1), DynamicNumber.of(pos.getZ() + 1)
        );
    }

    // ---------- 包含检测 ----------
    public boolean contains(BigVec3 point) {
        return contains(point.bigX(), point.bigY(), point.bigZ());
    }

    public boolean contains(DynamicNumber x, DynamicNumber y, DynamicNumber z) {
        return x.compareTo(getMinX()) >= 0 &&
                x.compareTo(getMaxX()) < 0 &&
                y.compareTo(getMinY()) >= 0 &&
                y.compareTo(getMaxY()) < 0 &&
                z.compareTo(getMinZ()) >= 0 &&
                z.compareTo(getMaxZ()) < 0;
    }

    public boolean contains(Vec3 point) {
        return contains(DynamicNumber.of(point.x), DynamicNumber.of(point.y), DynamicNumber.of(point.z));
    }

    // ---------- 相交并集 ----------
    public BigAABB intersect(BigAABB other) {
        return new BigAABB(
        BigMath.max(getMinX(), other.getMinX()),
        BigMath.max(getMinY(), other.getMinY()),
        BigMath.max(getMinZ(), other.getMinZ()),
        BigMath.min(getMaxX(), other.getMaxX()),
        BigMath.min(getMaxY(), other.getMaxY()),
        BigMath.min(getMaxZ(), other.getMaxZ())
        );
    }

    public BigAABB minmax(BigAABB other) {
        return new BigAABB(
        BigMath.min(getMinX(), other.getMinX()),
        BigMath.min(getMinY(), other.getMinY()),
        BigMath.min(getMinZ(), other.getMinZ()),
        BigMath.max(getMaxX(), other.getMaxX()),
        BigMath.max(getMaxY(), other.getMaxY()),
        BigMath.max(getMaxZ(), other.getMaxZ())
        );
    }

    // ---------- 距离计算 ----------
    public DynamicNumber distanceToSqr(BigVec3 point) {
        DynamicNumber dx = BigMath.max(BigMath.max(getMinX().subtract(point.bigX()), point.bigX().subtract(getMaxX())), DynamicNumber.ZERO);
        DynamicNumber dy = BigMath.max(BigMath.max(getMinY().subtract(point.bigY()), point.bigY().subtract(getMaxY())), DynamicNumber.ZERO);
        DynamicNumber dz = BigMath.max(BigMath.max(getMinZ().subtract(point.bigZ()), point.bigZ().subtract(getMaxZ())), DynamicNumber.ZERO);
        return BigMath.square(dx).add(BigMath.square(dy)).add(BigMath.square(dz));
    }

    public DynamicNumber distanceToSqr(BigAABB other) {
        DynamicNumber dx = BigMath.max(BigMath.max(getMinX().subtract(other.getMaxX()), other.getMinX().subtract(getMaxX())), DynamicNumber.ZERO);
        DynamicNumber dy = BigMath.max(BigMath.max(getMinY().subtract(other.getMaxY()), other.getMinY().subtract(getMaxY())), DynamicNumber.ZERO);
        DynamicNumber dz = BigMath.max(BigMath.max(getMinZ().subtract(other.getMaxZ()), other.getMinZ().subtract(getMaxZ())), DynamicNumber.ZERO);
        return BigMath.square(dx).add(BigMath.square(dy)).add(BigMath.square(dz));
    }

    // ---------- 中心 ----------
    public BigVec3 getCenter() {
        return new BigVec3(
        getMinX().add(getMaxX()).divide(DynamicNumber.of(2)),
        getMinY().add(getMaxY()).divide(DynamicNumber.of(2)),
        getMinZ().add(getMaxZ()).divide(DynamicNumber.of(2))
        );
    }

    public BigVec3 getBottomCenter() {
        return new BigVec3(
        getMinX().add(getMaxX()).divide(DynamicNumber.of(2)),
        getMinY(),
        getMinZ().add(getMaxZ()).divide(DynamicNumber.of(2))
        );
    }

    // ---------- 转为原版 AABB（可能损失精度） ----------
    public AABB toAABB() {
        return new AABB(
        getMinX().doubleValue(), getMinY().doubleValue(), getMinZ().doubleValue(),
        getMaxX().doubleValue(), getMaxY().doubleValue(), getMaxZ().doubleValue()
        );
    }

    // ---------- 标准方法 ----------
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BigAABB that)) return false;
        return minPos.equals(that.minPos) && maxPos.equals(that.maxPos);
    }

    @Override
    public int hashCode() {
        return 31 * minPos.hashCode() + maxPos.hashCode();
    }

    @Override
    public String toString() {
        return "BigAABB[" + getMinX() + ", " + getMinY() + ", " + getMinZ() +
                "] -> [" + getMaxX() + ", " + getMaxY() + ", " + getMaxZ() + "]";
    }

    // ---------- 碰撞检测辅助（待实现） ----------
    // clip 方法较复杂，涉及射线与盒的相交计算，可基于 BigMath 实现，暂留空
    public Optional<BigVec3> clip(BigVec3 from, BigVec3 to) {
        // TODO: 实现 3D 线段与 AABB 的交点计算
        // 可用原版算法，但需改用 DynamicNumber 运算
        return Optional.empty();
    }
}