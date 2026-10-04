package net.minecraft.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.BitSet;

/**
 * 🔧 MCRe（C2ME opts-allocs 移植）：按 bits 大小缓存复用的 BitSet 线程池（ThreadLocal）。
 * 每线程一个池，get 时 clear 复用 —— 降矿脉生成等热路径的分配压力（BlockPos 对象化后 Android GC 更敏感）。
 * 注意：复用前提 = 调用方不持有返回引用、无嵌套调用（矿脉生成满足：局部变量 + 不递归）。
 */
public class BitSetCacheUtil {
    private static final ThreadLocal<Int2ObjectOpenHashMap<BitSet>> BITSETS = ThreadLocal.withInitial(Int2ObjectOpenHashMap::new);

    private BitSetCacheUtil() {
    }

    public static BitSet getCachedOrNewBitSet(final int bits) {
        final BitSet bitSet = BITSETS.get().computeIfAbsent(bits, BitSet::new);
        bitSet.clear();
        return bitSet;
    }
}