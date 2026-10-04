package net.minecraft.core;

import com.google.common.collect.AbstractIterator;
import io.netty.buffer.ByteBuf;
import me.noisefarlands.mcbig.util.Int256;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.entity.EntityAccess;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 无限世界 SectionPos，使用 Int256 存储坐标。
 * 所有打包/解包 long 的方法已被移除或标记为废弃（抛出 UnsupportedOperationException）。
 */
public final class SectionPos extends Vec3i implements Position {
    // ---------- 内部存储 ----------
    private final Int256 bigX;
    private final Int256 bigY;
    private final Int256 bigZ;

    // ---------- 构造 ----------
    private SectionPos(Int256 x, Int256 y, Int256 z) {
        super(x.intValue(), y.intValue(), z.intValue()); // 仅用于兼容 Vec3i，不推荐使用
        this.bigX = x;
        this.bigY = y;
        this.bigZ = z;
    }

    // ---------- 工厂方法 ----------
    public static SectionPos of(Int256 x, Int256 y, Int256 z) {
        return new SectionPos(x, y, z);
    }

    public static SectionPos of(long x, long y, long z) {
        return new SectionPos(Int256.of(x), Int256.of(y), Int256.of(z));
    }

    public static SectionPos of(BlockPos pos) {
        return new SectionPos(
                blockToSectionCoord(pos.getBigX()),
                blockToSectionCoord(pos.getBigY()),
                blockToSectionCoord(pos.getBigZ())
        );
    }

    public static SectionPos of(ChunkPos pos, long sectionY) {
        return of(pos.x, sectionY, pos.z);
    }

    public static SectionPos of(EntityAccess entity) {
        return of(entity.blockPosition());
    }

    public static SectionPos of(Position pos) {
        return new SectionPos(
                blockToSectionCoord(Int256.of(pos.x())),
                blockToSectionCoord(Int256.of(pos.y())),
                blockToSectionCoord(Int256.of(pos.z()))
        );
    }

    public static SectionPos bottomOf(ChunkAccess chunk) {
        return of(chunk.getPos(), chunk.getMinSectionY());
    }

    // ---------- 坐标转换 ----------
    public static Int256 blockToSectionCoord(Int256 blockCoord) {
        return blockCoord.shiftRight(4); // >> 4
    }

    public static Int256 blockToSectionCoord(double coord) {
        return Int256.of(Mth.floor(coord) >> 4);
    }

    public static Int256 sectionRelative(Int256 blockCoord) {
        return blockCoord.and(Int256.of(15));
    }

    public static Int256 sectionToBlockCoord(Int256 sectionCoord) {
        return sectionCoord.shiftLeft(4);
    }

    public static Int256 sectionToBlockCoord(Int256 sectionCoord, long offset) {
        return sectionToBlockCoord(sectionCoord).add(Int256.of(offset));
    }

    // ---------- Getter（兼容原版 API） ----------
    public Int256 getBigX() { return bigX; }
    public Int256 getBigY() { return bigY; }
    public Int256 getBigZ() { return bigZ; }

    @Override
    public int getX() { return bigX.intValue(); }
    @Override
    public int getY() { return bigY.intValue(); }
    @Override
    public int getZ() { return bigZ.intValue(); }

    @Override
    public double x() { return bigX.doubleValue(); }
    @Override
    public double y() { return bigY.doubleValue(); }
    @Override
    public double z() { return bigZ.doubleValue(); }

    // ---------- 偏移 ----------
    public SectionPos offset(Int256 dx, Int256 dy, Int256 dz) {
        if (dx.isZero() && dy.isZero() && dz.isZero()) return this;
        return new SectionPos(bigX.add(dx), bigY.add(dy), bigZ.add(dz));
    }

    public SectionPos offset(long dx, long dy, long dz) {
        return offset(Int256.of(dx), Int256.of(dy), Int256.of(dz));
    }

    public SectionPos relative(Direction dir) {
        return offset(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    // ---------- 块坐标边界 ----------
    public Int256 minBlockX() { return sectionToBlockCoord(bigX); }
    public Int256 minBlockY() { return sectionToBlockCoord(bigY); }
    public Int256 minBlockZ() { return sectionToBlockCoord(bigZ); }
    public Int256 maxBlockX() { return sectionToBlockCoord(bigX, 15); }
    public Int256 maxBlockY() { return sectionToBlockCoord(bigY, 15); }
    public Int256 maxBlockZ() { return sectionToBlockCoord(bigZ, 15); }

    public BlockPos origin() {
        return new BlockPos(minBlockX(), minBlockY(), minBlockZ());
    }

    public BlockPos center() {
        return origin().offset(8, 8, 8);
    }

    // ---------- ChunkPos ----------
    public ChunkPos chunk() {
        return new ChunkPos(bigX.longValue(), bigZ.longValue());
    }

    // ---------- 相对坐标编码（原版 API，使用 int 即可） ----------
    public static short sectionRelativePos(BlockPos pos) {
        int rx = (int) (pos.getBigX().longValue() & 15);
        int ry = (int) (pos.getBigY().longValue() & 15);
        int rz = (int) (pos.getBigZ().longValue() & 15);
        return (short) ((rx << 8) | (rz << 4) | ry);
    }

    public static int sectionRelativeX(short relative) {
        return (relative >>> 8) & 15;
    }

    public static int sectionRelativeY(short relative) {
        return (relative >>> 0) & 15;
    }

    public static int sectionRelativeZ(short relative) {
        return (relative >>> 4) & 15;
    }

    public int relativeToBlockX(short relative) {
        return (int) (minBlockX().longValue() + sectionRelativeX(relative));
    }

    public int relativeToBlockY(short relative) {
        return (int) (minBlockY().longValue() + sectionRelativeY(relative));
    }

    public int relativeToBlockZ(short relative) {
        return (int) (minBlockZ().longValue() + sectionRelativeZ(relative));
    }

    public BlockPos relativeToBlockPos(short relative) {
        return new BlockPos(relativeToBlockX(relative), relativeToBlockY(relative), relativeToBlockZ(relative));
    }

    // ---------- Stream ----------
    public Stream<BlockPos> blocksInside() {
        return BlockPos.betweenClosedStream(
                minBlockX().longValue(), minBlockY().longValue(), minBlockZ().longValue(),
                maxBlockX().longValue(), maxBlockY().longValue(), maxBlockZ().longValue()
        );
    }

    public static Stream<SectionPos> cube(SectionPos center, long radius) {
        Int256 r = Int256.of(radius);
        Int256 x0 = center.bigX.subtract(r);
        Int256 y0 = center.bigY.subtract(r);
        Int256 z0 = center.bigZ.subtract(r);
        Int256 x1 = center.bigX.add(r);
        Int256 y1 = center.bigY.add(r);
        Int256 z1 = center.bigZ.add(r);
        return betweenClosedStream(x0, y0, z0, x1, y1, z1);
    }

    public static Stream<SectionPos> aroundChunk(ChunkPos center, long radius, long minSection, long maxSection) {
        Int256 r = Int256.of(radius);
        Int256 minY = Int256.of(minSection);
        Int256 maxY = Int256.of(maxSection);
        return betweenClosedStream(
                Int256.of(center.x).subtract(r), minY, Int256.of(center.z).subtract(r),
                Int256.of(center.x).add(r), maxY, Int256.of(center.z).add(r)
        );
    }

    public static Stream<SectionPos> betweenClosedStream(Int256 minX, Int256 minY, Int256 minZ,
                                                         Int256 maxX, Int256 maxY, Int256 maxZ) {
        Int256 width = maxX.subtract(minX).add(Int256.ONE);
        Int256 height = maxY.subtract(minY).add(Int256.ONE);
        Int256 depth = maxZ.subtract(minZ).add(Int256.ONE);
        Int256 total = width.multiply(height).multiply(depth);
        return StreamSupport.stream(new AbstractIterator<SectionPos>() {
            private Int256 index = Int256.ZERO;
            @Override
            protected SectionPos computeNext() {
                if (index.compareTo(total) >= 0) return endOfData();
                Int256 x = minX.add(index.mod(width));
                Int256 slice = index.divide(width);
                Int256 y = minY.add(slice.mod(height));
                Int256 z = minZ.add(slice.divide(height));
                index = index.add(Int256.ONE);
                return new SectionPos(x, y, z);
            }
        }.spliterator(), false);
    }

    // ===== 移除所有打包方法（标记为废弃并抛出异常） =====
    @Deprecated
    public long asLong() {
        throw new UnsupportedOperationException("asLong not supported in infinite SectionPos");
    }

    @Deprecated
    public static long asLong(int x, int y, int z) {
        throw new UnsupportedOperationException("asLong not supported in infinite SectionPos");
    }

    @Deprecated
    public static long getZeroNode(long sectionNode) {
        throw new UnsupportedOperationException("getZeroNode not supported in infinite SectionPos");
    }

    // ---------- 序列化 ----------
    public static final StreamCodec<ByteBuf, SectionPos> STREAM_CODEC = new StreamCodec<ByteBuf, SectionPos>() {
        @Override
        public SectionPos decode(ByteBuf buf) {
            return new SectionPos(
                    Int256.of(buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong()),
                    Int256.of(buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong()),
                    Int256.of(buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong())
            );
        }
        @Override
        public void encode(ByteBuf buf, SectionPos pos) {
            buf.writeLong(pos.bigX.getHigh());
            buf.writeLong(pos.bigX.getMidHigh());
            buf.writeLong(pos.bigX.getMidLow());
            buf.writeLong(pos.bigX.getLow());
            buf.writeLong(pos.bigY.getHigh());
            buf.writeLong(pos.bigY.getMidHigh());
            buf.writeLong(pos.bigY.getMidLow());
            buf.writeLong(pos.bigY.getLow());
            buf.writeLong(pos.bigZ.getHigh());
            buf.writeLong(pos.bigZ.getMidHigh());
            buf.writeLong(pos.bigZ.getMidLow());
            buf.writeLong(pos.bigZ.getLow());
        }
    };

    // ---------- equals/hashCode ----------
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SectionPos)) return false;
        SectionPos that = (SectionPos) obj;
        return this.bigX.equals(that.bigX) && this.bigY.equals(that.bigY) && this.bigZ.equals(that.bigZ);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bigX, bigY, bigZ);
    }

    @Override
    public String toString() {
        return "SectionPos{x=" + bigX + ", y=" + bigY + ", z=" + bigZ + "}";
    }
}