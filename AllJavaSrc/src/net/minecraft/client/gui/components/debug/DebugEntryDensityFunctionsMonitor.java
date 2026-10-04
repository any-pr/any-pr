package net.minecraft.client.gui.components.debug;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

/**
 * 🔧 MCRe：Density Functions Monitor 条目 —— 监测密度函数（DensityFunctions 全部类型）计算的每个步骤、
 * 所有节点的返回值（"看看到底是到哪一步算出了 NaN" 的调试工具）。
 * <p>监测机制：条目开启时在<b>玩家位置（Terrain XYZ 的实际玩家坐标）</b>离线 compute 一次 final_density
 * 密度函数树——全树经 mapAll 包 {@link MonitoringDensityFunction}（randomState 不变时缓存复用，位置变化才重算）。
 * 各噪声节点内部自行 reposition 到偏移缩放后的地形坐标采样，所以步骤值 = 地形实际采样的值。</p>
 * <p>每行 = DFM/Steps-N- (类型名): 值（Double.toString 科学记数法）；NaN/Infinity 整行标红（§c）。</p>
 * <p>记录窗口：display 后 1 秒内有效（条目关自动失效，平时仅一次 volatile 读零开销）。
 * 不再监测生成中的任意区块 cell。</p>
 */
@OnlyIn(Dist.CLIENT)
public class DebugEntryDensityFunctionsMonitor implements DebugScreenEntry {
    private static volatile long lastRecordWindowMs = 0L;
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<List<String>> STEPS = ThreadLocal.withInitial(ArrayList::new);
    // 🔧 值完全一样的行去重：同"名称+值"只记录首次（O(1) 集合判重、保序）——必要数据不被顶下屏幕
    private static final ThreadLocal<Set<String>> SEEN_KEYS = ThreadLocal.withInitial(HashSet::new);
    private static volatile List<String> lastSnapshot = List.of();
    private static volatile DensityFunction cachedTree = null;
    private static volatile RandomState lastRandomState = null;

    public static boolean recording() {
        return Util.getMillis() - lastRecordWindowMs < 1000L;
    }

    public static void begin() {
        if (DEPTH.get() == 0) {
            STEPS.get().clear();
            SEEN_KEYS.get().clear();
        }

        DEPTH.set(DEPTH.get() + 1);
    }

    public static void end() {
        int depth = DEPTH.get() - 1;
        DEPTH.set(depth);
        if (depth == 0) {
            // 保持计算顺序（后序遍历）：NaN/Inf 行的缩进层级 + 前后步骤 = 判断非法值来自哪条计算链的特征，
            // 置顶会切断这个链上下文（大佬裁定）——非法值只靠红色行首 §c 标记
            lastSnapshot = List.copyOf(STEPS.get());
        }
    }

    public static void record(final String name, final double value) {
        if (!recording()) {
            return;
        }

        // 🔧 值完全一样的行不显示：同"名称+值"只保留首次（步骤号不同但内容相同 = 无显示价值）
        String key = "(" + name + "): " + String.valueOf(value);
        if (!SEEN_KEYS.get().add(key)) {
            return;
        }

        List<String> steps = STEPS.get();
        // 🔧 树深度缩进（record 时 DEPTH 已 begin+1 = 当前节点层级）：根无缩进，每深一层缩进两格——父子关系直观
        String indent = "  ".repeat(Math.max(0, DEPTH.get() - 1));
        String formatted = String.valueOf(value);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            steps.add("§c" + indent + "DFM/Steps-" + (steps.size() + 1) + "- (" + name + "): " + formatted);
        } else {
            steps.add(indent + "DFM/Steps-" + (steps.size() + 1) + "- (" + name + "): " + formatted);
        }
    }

    /** 🔧 MCRe：包装节点（离线 mapAll 的 visitor 用）——透传类（值=子节点值）跳过不包，消除同值重复行 */
    public static DensityFunction monitor(final DensityFunction function) {
        if (function instanceof DensityFunctions.MarkerOrMarked) {
            // interpolated/flat_cache/cache_2d/cache_once/cache_all_in_cell/blend_density 等 Marker 包装：
            // compute 只是把子节点的值透传上来，记录它们只会产生同值重复行——直接返回 delegate 不包
            return function;
        }

        return new MonitoringDensityFunction(function);
    }

    private @Nullable BlockPos lastPos = null;

    @Override
    public void display(
        final DebugScreenDisplayer displayer,
        final @Nullable Level serverOrClientLevel,
        final @Nullable LevelChunk clientChunk,
        final @Nullable LevelChunk serverChunk
    ) {
        if (serverOrClientLevel != null) {
            lastRecordWindowMs = Util.getMillis();
            Entity entity = Minecraft.getInstance().getCameraEntity();
            if (entity != null) {
                BlockPos feetPos = entity.blockPosition();
                if (!feetPos.equals(this.lastPos)) {
                    this.update(serverOrClientLevel, feetPos);
                }
            }
        }

        displayer.addToGroup(DebugGroups.DENSITY_FUNCTIONS_MONITOR, lastSnapshot);
    }

    /** 🔧 MCRe：离线监测——在玩家 block 坐标上 compute 一次 final_density 树，每步记录 */
    private void update(final Level level, final BlockPos feetPos) {
        this.lastPos = feetPos;
        ServerLevel serverLevel = level instanceof ServerLevel sl ? sl : null;
        if (serverLevel == null) {
            return;
        }

        RandomState randomState = serverLevel.getChunkSource().randomState();
        // final_density 全树包 Monitoring；randomState 不变时复用缓存（避免每帧 mapAll 重构）
        if (this.cachedTree == null || this.lastRandomState != randomState) {
            this.cachedTree = randomState.router().finalDensity().mapAll(DebugEntryDensityFunctionsMonitor::monitor);
            this.lastRandomState = randomState;
        }

        // 在 Terrain XYZ（玩家 block 坐标）上离线 compute——各噪声节点内部自行 reposition 到地形坐标
        this.cachedTree.compute(new DensityFunction.FunctionContext() {
            @Override
            public int blockX() {
                return (int)feetPos.getX();
            }

            @Override
            public int blockY() {
                return (int)feetPos.getY();
            }

            @Override
            public int blockZ() {
                return (int)feetPos.getZ();
            }
        });
        // compute 完成后 lastSnapshot 已发布（end 的根快照），display 直接显示
    }

    /**
     * 🔧 MCRe：密度函数监测包装——compute 时记录类型名 + 返回值。
     * 类型名：二元（add/mul/min/max）与 Marker（interpolated/flat_cache/...）取类型串，其余取类简名。
     * fillArray/mapChildren/minValue/maxValue/codec 全透传（Cache 类的值与其子节点同值不重复记录）。
     */
    public static class MonitoringDensityFunction implements DensityFunction {
        private final DensityFunction delegate;
        private final String name;

        public MonitoringDensityFunction(final DensityFunction delegate) {
            this.delegate = delegate;
            this.name = nameOf(delegate);
        }

        private static String nameOf(final DensityFunction f) {
            if (f instanceof DensityFunctions.TwoArgumentSimpleFunction two) {
                return two.type().getSerializedName();
            }

            if (f instanceof DensityFunctions.MarkerOrMarked marker) {
                return marker.type().getSerializedName();
            }

            String s = f.getClass().getSimpleName();
            return s.isEmpty() ? "anonymous" : s;
        }

        @Override
        public double compute(final DensityFunction.FunctionContext context) {
            // 🔧 双检查限流：只有记录窗口开着才走 begin/end/record，其余直接委托零开销
            if (recording()) {
                begin();
                try {
                    double value = this.delegate.compute(context);
                    record(this.name, value);
                    return value;
                } finally {
                    end();
                }
            }

            return this.delegate.compute(context);
        }

        @Override
        public void fillArray(final double[] output, final DensityFunction.ContextProvider contextProvider) {
            this.delegate.fillArray(output, contextProvider);
        }

        @Override
        public DensityFunction mapChildren(final DensityFunction.Visitor visitor) {
            return this.delegate.mapChildren(visitor);
        }

        @Override
        public double minValue() {
            return this.delegate.minValue();
        }

        @Override
        public double maxValue() {
            return this.delegate.maxValue();
        }

        @Override
        public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.delegate.codec();
        }
    }
}