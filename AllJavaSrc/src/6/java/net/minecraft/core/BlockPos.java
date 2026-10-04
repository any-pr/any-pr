package net.minecraft.core;

import com.google.common.collect.AbstractIterator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.noisefarlands.mcbig.util.Int256;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Immutable
public class BlockPos extends Vec3i implements Position {
    // ---------- 序列化 ----------
    public static final Codec<BlockPos> CODEC = Codec.INT_STREAM
            .comapFlatMap(
                    input -> Util.fixedSize(input, 3).map(arr -> new BlockPos(arr[0], arr[1], arr[2])),
                    pos -> java.util.stream.IntStream.of(pos.getX(), pos.getY(), pos.getZ())
            )
            .stable();

    public static final StreamCodec<ByteBuf, BlockPos> STREAM_CODEC = new StreamCodec<ByteBuf, BlockPos>() {
        @Override
        public BlockPos decode(ByteBuf buf) {
            return new BlockPos(FriendlyByteBuf.readInt256(buf),
                                FriendlyByteBuf.readInt256(buf),
                                FriendlyByteBuf.readInt256(buf));
        }
        @Override
        public void encode(ByteBuf buf, BlockPos pos) {
            FriendlyByteBuf.writeInt256(buf, pos.bigX);
            FriendlyByteBuf.writeInt256(buf, pos.bigY);
            FriendlyByteBuf.writeInt256(buf, pos.bigZ);
        }
    };

    public static final BlockPos ZERO = new BlockPos(Int256.ZERO, Int256.ZERO, Int256.ZERO);

    // ---------- 内部存储 ----------
    private final Int256 bigX;
    private final Int256 bigY;
    private final Int256 bigZ;

    // ---------- 构造 ----------
    public BlockPos(int x, int y, int z) {
        super(x, y, z);
        this.bigX = Int256.of(x);
        this.bigY = Int256.of(y);
        this.bigZ = Int256.of(z);
    }

    public BlockPos(long x, long y, long z) {
        this((int) x, (int) y, (int) z);
    }

    public BlockPos(Int256 x, Int256 y, Int256 z) {
        super(x.intValue(), y.intValue(), z.intValue());
        this.bigX = x;
        this.bigY = y;
        this.bigZ = z;
    }

    public BlockPos(Vec3i vec) {
        this(vec.getX(), vec.getY(), vec.getZ());
    }

    // ---------- Getter / Setter ----------
    @Override
    public int getX() { return bigX.intValue(); }
    @Override
    public int getY() { return bigY.intValue(); }
    @Override
    public int getZ() { return bigZ.intValue(); }

    public Int256 getBigX() { return bigX; }
    public Int256 getBigY() { return bigY; }
    public Int256 getBigZ() { return bigZ; }

    @Override
    public double x() { return bigX.doubleValue(); }
    @Override
    public double y() { return bigY.doubleValue(); }
    @Override
    public double z() { return bigZ.doubleValue(); }

    // ---------- 工厂方法 ----------
    public static BlockPos of(Int256 x, Int256 y, Int256 z) {
        return new BlockPos(x, y, z);
    }

    public static BlockPos of(long x, long y, long z) {
        return new BlockPos(x, y, z);
    }

    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos(Int256.of(Mth.floor(x)), Int256.of(Mth.floor(y)), Int256.of(Mth.floor(z)));
    }

    public static BlockPos containing(Position pos) {
        return containing(pos.x(), pos.y(), pos.z());
    }

    public static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(a.bigX.min(b.bigX), a.bigY.min(b.bigY), a.bigZ.min(b.bigZ));
    }

    public static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(a.bigX.max(b.bigX), a.bigY.max(b.bigY), a.bigZ.max(b.bigZ));
    }

    // ---------- 偏移 ----------
    @Override
    public BlockPos offset(int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) return this;
        return new BlockPos(bigX.add(Int256.of(dx)),
                            bigY.add(Int256.of(dy)),
                            bigZ.add(Int256.of(dz)));
    }

    public BlockPos offset(Int256 dx, Int256 dy, Int256 dz) {
        if (dx.isZero() && dy.isZero() && dz.isZero()) return this;
        return new BlockPos(bigX.add(dx), bigY.add(dy), bigZ.add(dz));
    }

    @Override
    public BlockPos offset(Vec3i vec) {
        return offset(vec.getX(), vec.getY(), vec.getZ());
    }

    public BlockPos offset(Direction dir) {
        return offset(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    @Override
    public BlockPos subtract(Vec3i vec) {
        return offset(-vec.getX(), -vec.getY(), -vec.getZ());
    }

    @Override
    public BlockPos multiply(int scale) {
        if (scale == 1) return this;
        if (scale == 0) return ZERO;
        return new BlockPos(bigX.multiply(Int256.of(scale)),
                            bigY.multiply(Int256.of(scale)),
                            bigZ.multiply(Int256.of(scale)));
    }

    // ---------- 方向辅助 ----------
    @Override
    public BlockPos above() { return relative(Direction.UP); }
    @Override
    public BlockPos above(int steps) { return relative(Direction.UP, steps); }
    @Override
    public BlockPos below() { return relative(Direction.DOWN); }
    @Override
    public BlockPos below(int steps) { return relative(Direction.DOWN, steps); }
    @Override
    public BlockPos north() { return relative(Direction.NORTH); }
    @Override
    public BlockPos north(int steps) { return relative(Direction.NORTH, steps); }
    @Override
    public BlockPos south() { return relative(Direction.SOUTH); }
    @Override
    public BlockPos south(int steps) { return relative(Direction.SOUTH, steps); }
    @Override
    public BlockPos west() { return relative(Direction.WEST); }
    @Override
    public BlockPos west(int steps) { return relative(Direction.WEST, steps); }
    @Override
    public BlockPos east() { return relative(Direction.EAST); }
    @Override
    public BlockPos east(int steps) { return relative(Direction.EAST, steps); }

    @Override
    public BlockPos relative(Direction dir) {
        return offset(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    @Override
    public BlockPos relative(Direction dir, int steps) {
        if (steps == 0) return this;
        return offset(dir.getStepX() * steps, dir.getStepY() * steps, dir.getStepZ() * steps);
    }

    @Override
    public BlockPos relative(Direction.Axis axis, int steps) {
        if (steps == 0) return this;
        int dx = axis == Direction.Axis.X ? steps : 0;
        int dy = axis == Direction.Axis.Y ? steps : 0;
        int dz = axis == Direction.Axis.Z ? steps : 0;
        return offset(dx, dy, dz);
    }

    // ---------- 旋转 ----------
    @Override
    public BlockPos rotate(Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_90:  return new BlockPos(bigZ.negate(), bigY, bigX);
            case CLOCKWISE_180: return new BlockPos(bigX.negate(), bigY, bigZ.negate());
            case COUNTERCLOCKWISE_90: return new BlockPos(bigZ, bigY, bigX.negate());
            default: return this;
        }
    }

    @Override
    public BlockPos cross(Vec3i upVector) {
        Int256 vx = Int256.of(upVector.getX());
        Int256 vy = Int256.of(upVector.getY());
        Int256 vz = Int256.of(upVector.getZ());
        return new BlockPos(
                bigY.multiply(vz).subtract(bigZ.multiply(vy)),
                bigZ.multiply(vx).subtract(bigX.multiply(vz)),
                bigX.multiply(vy).subtract(bigY.multiply(vx))
        );
    }

    public BlockPos atY(int y) {
        return new BlockPos(bigX, Int256.of(y), bigZ);
    }

    // ---------- Vec3 转换 ----------
    public Vec3 getCenter() { return Vec3.atCenterOf(this); }
    public Vec3 getBottomCenter() { return Vec3.atBottomCenterOf(this); }
    public Vec3 clampLocationWithin(Vec3 location) {
        return new Vec3(
                Mth.clamp(location.x, bigX.doubleValue() + 1.0E-5F, bigX.doubleValue() + 1.0 - 1.0E-5F),
                Mth.clamp(location.y, bigY.doubleValue() + 1.0E-5F, bigY.doubleValue() + 1.0 - 1.0E-5F),
                Mth.clamp(location.z, bigZ.doubleValue() + 1.0E-5F, bigZ.doubleValue() + 1.0 - 1.0E-5F)
        );
    }

    // ---------- 不可变与可变 ----------
    @Override
    public BlockPos immutable() { return this; }
    public MutableBlockPos mutable() { return new MutableBlockPos(bigX, bigY, bigZ); }

    // ---------- 距离方法 ----------
    @Override
    public boolean closerThan(Vec3i pos, double distance) {
        return this.distSqr(pos) < Mth.square(distance);
    }

    @Override
    public boolean closerToCenterThan(Position pos, double distance) {
        return this.distToCenterSqr(pos) < Mth.square(distance);
    }

    @Override
    public double distSqr(Vec3i pos) {
        return this.distToLowCornerSqr(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public double distToCenterSqr(Position pos) {
        return this.distToCenterSqr(pos.x(), pos.y(), pos.z());
    }

    @Override
    public double distToCenterSqr(double x, double y, double z) {
        double dx = bigX.doubleValue() + 0.5 - x;
        double dy = bigY.doubleValue() + 0.5 - y;
        double dz = bigZ.doubleValue() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public double distToLowCornerSqr(double x, double y, double z) {
        double dx = bigX.doubleValue() - x;
        double dy = bigY.doubleValue() - y;
        double dz = bigZ.doubleValue() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public int distManhattan(Vec3i pos) {
        return bigX.subtract(Int256.of(pos.getX())).abs().intValue()
                + bigY.subtract(Int256.of(pos.getY())).abs().intValue()
                + bigZ.subtract(Int256.of(pos.getZ())).abs().intValue();
    }

    @Override
    public int distChessboard(Vec3i pos) {
        Int256 dx = bigX.subtract(Int256.of(pos.getX())).abs();
        Int256 dy = bigY.subtract(Int256.of(pos.getY())).abs();
        Int256 dz = bigZ.subtract(Int256.of(pos.getZ())).abs();
        return dx.max(dy).max(dz).intValue();
    }

    @Override
    public int get(Direction.Axis axis) {
        switch (axis) {
            case X: return bigX.intValue();
            case Y: return bigY.intValue();
            case Z: return bigZ.intValue();
            default: return 0;
        }
    }

    // ---------- 标准方法 ----------
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockPos other)) return false;
        return bigX.equals(other.bigX) && bigY.equals(other.bigY) && bigZ.equals(other.bigZ);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bigX, bigY, bigZ);
    }

    @Override
    public int compareTo(Vec3i o) {
        if (!(o instanceof BlockPos other)) {
            // 降级为 Vec3i 比较（按 y, z, x）
            return super.compareTo(o);
        }
        int yComp = bigY.compareTo(other.bigY);
        if (yComp != 0) return yComp;
        int zComp = bigZ.compareTo(other.bigZ);
        if (zComp != 0) return zComp;
        return bigX.compareTo(other.bigX);
    }

    @Override
    public String toString() {
        return "BlockPos(" + bigX + ", " + bigY + ", " + bigZ + ")";
    }

    // ---------- 移除所有 asLong 方法 ----------
    @Deprecated
    public long asLong() { throw new UnsupportedOperationException("asLong not supported in infinite BlockPos"); }
    @Deprecated
    public static long asLong(int x, int y, int z) { throw new UnsupportedOperationException("asLong not supported"); }
    @Deprecated
    public static long offset(long blockNode, Direction dir) { throw new UnsupportedOperationException(); }
    @Deprecated
    public static long offset(long blockNode, int dx, int dy, int dz) { throw new UnsupportedOperationException(); }
    @Deprecated
    public static int getX(long blockNode) { throw new UnsupportedOperationException(); }
    @Deprecated
    public static int getY(long blockNode) { throw new UnsupportedOperationException(); }
    @Deprecated
    public static int getZ(long blockNode) { throw new UnsupportedOperationException(); }
    @Deprecated
    public static BlockPos of(long packed) { throw new UnsupportedOperationException(); }
    @Deprecated
    public static long getFlatIndex(long neighborBlockNode) { throw new UnsupportedOperationException(); }

    // ===== 静态迭代器方法（全部使用 Int256） =====

    public static Iterable<BlockPos> betweenClosed(BlockPos a, BlockPos b) {
        return betweenClosed(a.bigX, a.bigY, a.bigZ, b.bigX, b.bigY, b.bigZ);
    }

    public static Iterable<BlockPos> betweenClosed(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return betweenClosed(Int256.of(minX), Int256.of(minY), Int256.of(minZ),
                             Int256.of(maxX), Int256.of(maxY), Int256.of(maxZ));
    }

    public static Iterable<BlockPos> betweenClosed(Int256 minX, Int256 minY, Int256 minZ,
                                                   Int256 maxX, Int256 maxY, Int256 maxZ) {
        Int256 width = maxX.subtract(minX).add(Int256.ONE);
        Int256 height = maxY.subtract(minY).add(Int256.ONE);
        Int256 depth = maxZ.subtract(minZ).add(Int256.ONE);
        Int256 total = width.multiply(height).multiply(depth);
        return () -> new AbstractIterator<BlockPos>() {
            private final MutableBlockPos cursor = new MutableBlockPos();
            private Int256 index = Int256.ZERO;
            @Override
            protected BlockPos computeNext() {
                if (index.compareTo(total) >= 0) return endOfData();
                Int256 x = index.mod(width);
                Int256 slice = index.divide(width);
                Int256 y = slice.mod(height);
                Int256 z = slice.divide(height);
                index = index.add(Int256.ONE);
                return cursor.set(minX.add(x), minY.add(y), minZ.add(z));
            }
        };
    }

    public static Stream<BlockPos> betweenClosedStream(BlockPos a, BlockPos b) {
        return StreamSupport.stream(betweenClosed(a, b).spliterator(), false);
    }

    public static Stream<BlockPos> betweenClosedStream(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return betweenClosedStream(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    // ===== withinManhattan =====
    public static Iterable<BlockPos> withinManhattan(BlockPos origin, int reachX, int reachY, int reachZ) {
        int total = reachX + reachY + reachZ;
        Int256 ox = origin.bigX, oy = origin.bigY, oz = origin.bigZ;
        return () -> new AbstractIterator<BlockPos>() {
            private final MutableBlockPos cursor = new MutableBlockPos();
            private int currentDepth = 0;
            private long maxX = 0, maxY = 0, x = 0, y = 0;
            private boolean zMirror = false;
            @Override
            protected BlockPos computeNext() {
                if (zMirror) {
                    zMirror = false;
                    cursor.setZ(oz.subtract(cursor.bigZ.subtract(oz)));
                    return cursor;
                }
                BlockPos found = null;
                while (found == null) {
                    if (y > maxY) {
                        x++;
                        if (x > maxX) {
                            currentDepth++;
                            if (currentDepth > total) return endOfData();
                            maxX = Math.min(reachX, currentDepth);
                            x = -maxX;
                        }
                        maxY = Math.min(reachY, currentDepth - Math.abs(x));
                        y = -maxY;
                    }
                    long xx = x, yy = y;
                    long zz = currentDepth - Math.abs(xx) - Math.abs(yy);
                    if (zz <= reachZ) {
                        zMirror = (zz != 0);
                        found = cursor.set(ox.add(Int256.of(xx)),
                                           oy.add(Int256.of(yy)),
                                           oz.add(Int256.of(zz)));
                    }
                    y++;
                }
                return found;
            }
        };
    }

    public static Stream<BlockPos> withinManhattanStream(BlockPos origin, int reachX, int reachY, int reachZ) {
        return StreamSupport.stream(withinManhattan(origin, reachX, reachY, reachZ).spliterator(), false);
    }

    // ===== 随机 =====
    public static Iterable<BlockPos> randomInCube(RandomSource random, int limit, BlockPos center, int radius) {
        return randomBetweenClosed(random, limit,
                center.bigX.subtract(Int256.of(radius)),
                center.bigY.subtract(Int256.of(radius)),
                center.bigZ.subtract(Int256.of(radius)),
                center.bigX.add(Int256.of(radius)),
                center.bigY.add(Int256.of(radius)),
                center.bigZ.add(Int256.of(radius))
        );
    }

    public static Iterable<BlockPos> randomBetweenClosed(RandomSource random, int limit,
                                                         Int256 minX, Int256 minY, Int256 minZ,
                                                         Int256 maxX, Int256 maxY, Int256 maxZ) {
        Int256 width = maxX.subtract(minX).add(Int256.ONE);
        Int256 height = maxY.subtract(minY).add(Int256.ONE);
        Int256 depth = maxZ.subtract(minZ).add(Int256.ONE);
        return () -> new AbstractIterator<BlockPos>() {
            private final MutableBlockPos nextPos = new MutableBlockPos();
            private int counter = limit;
            @Override
            protected BlockPos computeNext() {
                if (counter <= 0) return endOfData();
                // 生成随机数 (使用 RandomSource 的 nextLong)
                Int256 rx = minX.add(Int256.of(random.nextLong() % width.longValue()));
                Int256 ry = minY.add(Int256.of(random.nextLong() % height.longValue()));
                Int256 rz = minZ.add(Int256.of(random.nextLong() % depth.longValue()));
                nextPos.set(rx, ry, rz);
                counter--;
                return nextPos;
            }
        };
    }

    // ===== 广度优先遍历 =====
    public static int breadthFirstTraversal(BlockPos start, int maxDepth, int maxCount,
                                            BiConsumer<BlockPos, Consumer<BlockPos>> neighborProvider,
                                            Function<BlockPos, TraversalNodeStatus> processor) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        int count = 0;
        int depth = 0;
        while (!queue.isEmpty() && depth <= maxDepth) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                BlockPos pos = queue.poll();
                TraversalNodeStatus status = processor.apply(pos);
                if (status == TraversalNodeStatus.STOP) return count;
                if (status == TraversalNodeStatus.SKIP) continue;
                if (++count >= maxCount) return count;
                if (depth < maxDepth) {
                    neighborProvider.accept(pos, p -> {
                        if (visited.add(p)) queue.add(p);
                    });
                }
            }
            depth++;
        }
        return count;
    }

    // ===== 内部可变类 =====
    public static final class MutableBlockPos extends BlockPos {
        public MutableBlockPos() { this(Int256.ZERO, Int256.ZERO, Int256.ZERO); }
        public MutableBlockPos(Int256 x, Int256 y, Int256 z) { super(x, y, z); }
        public MutableBlockPos(int x, int y, int z) { super(x, y, z); }
        public MutableBlockPos(long x, long y, long z) { super(x, y, z); }
        public MutableBlockPos(double x, double y, double z) {
            this(Int256.of(Mth.floor(x)), Int256.of(Mth.floor(y)), Int256.of(Mth.floor(z)));
        }

        @Override public BlockPos offset(int dx, int dy, int dz) { return super.offset(dx, dy, dz).immutable(); }
        @Override public BlockPos multiply(int scale) { return super.multiply(scale).immutable(); }
        @Override public BlockPos relative(Direction dir, int steps) { return super.relative(dir, steps).immutable(); }
        @Override public BlockPos relative(Direction.Axis axis, int steps) { return super.relative(axis, steps).immutable(); }
        @Override public BlockPos rotate(Rotation rotation) { return super.rotate(rotation).immutable(); }

        public MutableBlockPos set(Int256 x, Int256 y, Int256 z) {
            unsafeSetX(x); unsafeSetY(y); unsafeSetZ(z);
            return this;
        }
        public MutableBlockPos set(long x, long y, long z) { return set(Int256.of(x), Int256.of(y), Int256.of(z)); }
        public MutableBlockPos set(int x, int y, int z) { return set(Int256.of(x), Int256.of(y), Int256.of(z)); }
        public MutableBlockPos set(double x, double y, double z) {
            return set(Int256.of(Mth.floor(x)), Int256.of(Mth.floor(y)), Int256.of(Mth.floor(z)));
        }
        public MutableBlockPos set(BlockPos pos) { return set(pos.bigX, pos.bigY, pos.bigZ); }

        public MutableBlockPos move(Direction dir) { return move(dir, 1); }
        public MutableBlockPos move(Direction dir, int steps) {
            return set(bigX.add(Int256.of((long) dir.getStepX() * steps)),
                       bigY.add(Int256.of((long) dir.getStepY() * steps)),
                       bigZ.add(Int256.of((long) dir.getStepZ() * steps)));
        }
        public MutableBlockPos move(int dx, int dy, int dz) {
            return set(bigX.add(Int256.of(dx)), bigY.add(Int256.of(dy)), bigZ.add(Int256.of(dz)));
        }
        public MutableBlockPos move(Vec3i pos) {
            return set(bigX.add(Int256.of(pos.getX())), bigY.add(Int256.of(pos.getY())), bigZ.add(Int256.of(pos.getZ())));
        }

        public MutableBlockPos setX(Int256 x) { unsafeSetX(x); return this; }
        public MutableBlockPos setY(Int256 y) { unsafeSetY(y); return this; }
        public MutableBlockPos setZ(Int256 z) { unsafeSetZ(z); return this; }

        private void unsafeSetX(Int256 x) { super.setX(x.intValue()); this.bigX = x; }
        private void unsafeSetY(Int256 y) { super.setY(y.intValue()); this.bigY = y; }
        private void unsafeSetZ(Int256 z) { super.setZ(z.intValue()); this.bigZ = z; }

        @Override public BlockPos immutable() { return new BlockPos(bigX, bigY, bigZ); }
    }

    public enum TraversalNodeStatus {
        ACCEPT, SKIP, STOP;
    }
}