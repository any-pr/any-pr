package net.minecraft.world.level;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import me.noisefarlands.mcbig.util.Int256;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class ChunkPos {
    // ========== 常量和序列化 ==========
    public static final Codec<ChunkPos> CODEC = Codec.INT_STREAM
        .comapFlatMap(
            input -> Util.fixedSize(input, 2).map(ints -> new ChunkPos(Int256.of(ints[0]), Int256.of(ints[1]))),
            pos -> IntStream.of(pos.bigX.intValue(), pos.bigZ.intValue())
        )
        .stable();

    public static final StreamCodec<ByteBuf, ChunkPos> STREAM_CODEC = new StreamCodec<ByteBuf, ChunkPos>() {
        @Override
        public ChunkPos decode(ByteBuf buf) {
            return new ChunkPos(
                Int256.of(buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong()),
                Int256.of(buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong())
            );
        }
        @Override
        public void encode(ByteBuf buf, ChunkPos pos) {
            buf.writeLong(pos.bigX.getHigh());
            buf.writeLong(pos.bigX.getMidHigh());
            buf.writeLong(pos.bigX.getMidLow());
            buf.writeLong(pos.bigX.getLow());
            buf.writeLong(pos.bigZ.getHigh());
            buf.writeLong(pos.bigZ.getMidHigh());
            buf.writeLong(pos.bigZ.getMidLow());
            buf.writeLong(pos.bigZ.getLow());
        }
    };

    // ========== 常量（使用 Int256） ==========
    private static final Int256 SAFETY_MARGIN = Int256.of(1056);
    // INVALID_CHUNK_POS 使用一个极端的 Int256 值（不在有效范围内）
    public static final Int256 INVALID_CHUNK_POS = Int256.MAX_VALUE; // 或者 null 语义，但保留常量
    private static final Int256 SAFETY_MARGIN_CHUNKS = Int256.of(
        (32 + ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FULL).accumulatedDependencies().size() + 1) * 2
    );
    // ===== MCRe: 彻底突破 32 位限制，使用 Int256.MAX_VALUE =====
    public static final Int256 MAX_COORDINATE_VALUE = Int256.MAX_VALUE;
    public static final ChunkPos ZERO = new ChunkPos(Int256.ZERO, Int256.ZERO);

    // ========== 字段 ==========
    public final Int256 bigX;
    public final Int256 bigZ;

    // ========== 构造 ==========
    public ChunkPos(Int256 x, Int256 z) {
        this.bigX = x;
        this.bigZ = z;
    }

    public ChunkPos(long x, long z) {
        this(Int256.of(x), Int256.of(z));
    }

    public ChunkPos(BlockPos pos) {
        this(SectionPos.blockToSectionCoord(pos.getBigX()),
             SectionPos.blockToSectionCoord(pos.getBigZ()));
    }

    @Deprecated
    public ChunkPos(long packed) {
        this((int)packed, (int)(packed >> 32));
        throw new UnsupportedOperationException("ChunkPos(long) is deprecated; use object factories.");
    }

    // ========== 访问器 ==========
    public Int256 getBigX() { return bigX; }
    public Int256 getBigZ() { return bigZ; }

    // 兼容原版（可能溢出）
    public int getX() { return bigX.intValue(); }
    public int getZ() { return bigZ.intValue(); }

    // ========== 静态工厂 ==========
    public static ChunkPos minFromRegion(Int256 regionX, Int256 regionZ) {
        return new ChunkPos(regionX.shiftLeft(5), regionZ.shiftLeft(5));
    }

    public static ChunkPos maxFromRegion(Int256 regionX, Int256 regionZ) {
        return new ChunkPos(regionX.shiftLeft(5).add(31), regionZ.shiftLeft(5).add(31));
    }

    // ========== 有效性 ==========
    public boolean isValid() {
        return isValid(this.bigX, this.bigZ);
    }

    public static boolean isValid(Int256 x, Int256 z) {
        return x.abs().compareTo(MAX_COORDINATE_VALUE) <= 0 &&
               z.abs().compareTo(MAX_COORDINATE_VALUE) <= 0;
    }

    // ========== 废弃的打包方法 ==========
    @Deprecated
    public long toLong() {
        throw new UnsupportedOperationException("toLong() is deprecated; use object references.");
    }

    @Deprecated
    public static long asLong(int x, int z) {
        throw new UnsupportedOperationException("asLong(int,int) is deprecated.");
    }

    @Deprecated
    public static long asLong(BlockPos pos) {
        throw new UnsupportedOperationException("asLong(BlockPos) is deprecated.");
    }

    @Deprecated
    public static int getX(long pos) {
        throw new UnsupportedOperationException("getX(long) is deprecated.");
    }

    @Deprecated
    public static int getZ(long pos) {
        throw new UnsupportedOperationException("getZ(long) is deprecated.");
    }

    // ========== 哈希（使用 Int256） ==========
    @Override
    public int hashCode() {
        return bigX.hashCode() ^ bigZ.hashCode();
    }

    // ========== 原版实例方法（全部返回 Int256） ==========
    public Int256 getMiddleBlockX() {
        return SectionPos.sectionToBlockCoord(this.bigX).add(8);
    }

    public Int256 getMiddleBlockZ() {
        return SectionPos.sectionToBlockCoord(this.bigZ).add(8);
    }

    public Int256 getMinBlockX() {
        return SectionPos.sectionToBlockCoord(this.bigX);
    }

    public Int256 getMinBlockZ() {
        return SectionPos.sectionToBlockCoord(this.bigZ);
    }

    public Int256 getMaxBlockX() {
        return SectionPos.sectionToBlockCoord(this.bigX, 15);
    }

    public Int256 getMaxBlockZ() {
        return SectionPos.sectionToBlockCoord(this.bigZ, 15);
    }

    public Int256 getRegionX() {
        return this.bigX.shiftRight(5);
    }

    public Int256 getRegionZ() {
        return this.bigZ.shiftRight(5);
    }

    public Int256 getRegionLocalX() {
        return this.bigX.and(31);
    }

    public Int256 getRegionLocalZ() {
        return this.bigZ.and(31);
    }

    public Int256 getBlockX(int offset) {
        return SectionPos.sectionToBlockCoord(this.bigX, offset);
    }

    public Int256 getBlockZ(int offset) {
        return SectionPos.sectionToBlockCoord(this.bigZ, offset);
    }

    public BlockPos getBlockAt(int x, int y, int z) {
        return new BlockPos(this.getBlockX(x).longValue(), y, this.getBlockZ(z).longValue());
    }

    public BlockPos getMiddleBlockPosition(int y) {
        return new BlockPos(this.getMiddleBlockX().longValue(), y, this.getMiddleBlockZ().longValue());
    }

    public BlockPos getWorldPosition() {
        return new BlockPos(this.getMinBlockX().longValue(), 0, this.getMinBlockZ().longValue());
    }

    public boolean contains(BlockPos pos) {
        return pos.getBigX().compareTo(this.getMinBlockX()) >= 0 &&
               pos.getBigZ().compareTo(this.getMinBlockZ()) >= 0 &&
               pos.getBigX().compareTo(this.getMaxBlockX()) <= 0 &&
               pos.getBigZ().compareTo(this.getMaxBlockZ()) <= 0;
    }

    // ===== 距离方法 =====
    public Int256 getChessboardDistance(ChunkPos pos) {
        Int256 dx = this.bigX.subtract(pos.bigX).abs();
        Int256 dz = this.bigZ.subtract(pos.bigZ).abs();
        return dx.max(dz);
    }

    public Int256 getChessboardDistance(Int256 x, Int256 z) {
        Int256 dx = this.bigX.subtract(x).abs();
        Int256 dz = this.bigZ.subtract(z).abs();
        return dx.max(dz);
    }

    public Int256 distanceSquared(ChunkPos pos) {
        return distanceSquared(pos.bigX, pos.bigZ);
    }

    public Int256 distanceSquared(Int256 x, Int256 z) {
        Int256 dx = x.subtract(this.bigX);
        Int256 dz = z.subtract(this.bigZ);
        return dx.multiply(dx).add(dz.multiply(dz));
    }

    // ========== Stream 方法 ==========
    public static Stream<ChunkPos> rangeClosed(ChunkPos center, long radius) {
        return rangeClosed(
            new ChunkPos(center.bigX.subtract(Int256.of(radius)), center.bigZ.subtract(Int256.of(radius))),
            new ChunkPos(center.bigX.add(Int256.of(radius)), center.bigZ.add(Int256.of(radius)))
        );
    }

    public static Stream<ChunkPos> rangeClosed(ChunkPos from, ChunkPos to) {
        Int256 xSize = from.bigX.subtract(to.bigX).abs().add(Int256.ONE);
        Int256 zSize = from.bigZ.subtract(to.bigZ).abs().add(Int256.ONE);
        final int xDiff = from.bigX.compareTo(to.bigX) < 0 ? 1 : -1;
        final int zDiff = from.bigZ.compareTo(to.bigZ) < 0 ? 1 : -1;
        long total = xSize.longValue() * zSize.longValue();
        return StreamSupport.stream(new AbstractSpliterator<ChunkPos>((int)total, 64) {
            private @Nullable ChunkPos pos;

            @Override
            public boolean tryAdvance(Consumer<? super ChunkPos> action) {
                if (this.pos == null) {
                    this.pos = from;
                } else {
                    Int256 x = this.pos.bigX;
                    Int256 z = this.pos.bigZ;
                    if (x.equals(to.bigX)) {
                        if (z.equals(to.bigZ)) return false;
                        this.pos = new ChunkPos(from.bigX, z.add(Int256.of(zDiff)));
                    } else {
                        this.pos = new ChunkPos(x.add(Int256.of(xDiff)), z);
                    }
                }
                action.accept(this.pos);
                return true;
            }
        }, false);
    }

    // ========== equals/toString ==========
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ChunkPos)) return false;
        ChunkPos that = (ChunkPos) obj;
        return this.bigX.equals(that.bigX) && this.bigZ.equals(that.bigZ);
    }

    @Override
    public String toString() {
        return "[" + this.bigX + ", " + this.bigZ + "]";
    }
}