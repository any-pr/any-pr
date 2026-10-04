package net.minecraft.client.gui.components.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldReposition;
import net.minecraft.world.level.levelgen.synth.NoiseOverflowUtil;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

/**
 * 🔧 MCRe：AllNoiseList 条目（组：Terrain Noises）——为地形生成器使用的每个噪声显示当前玩家位置的最终返回值。
 * <p>数据源：RandomState 的噪声实例缓存（{@code getOrCreateNoise} 的 computeIfAbsent map）——地形生成器
 * （NoiseRouterData 的密度函数链 / SurfaceSystem / SurfaceRules）用到的噪声全部经它实例化，
 * 遍历即覆盖全部实际在用的噪声（如 BADLANDS/ICEBERG 系列没用到就不会出现在列表）。</p>
 * <p>名称取 NoiseParameters 的 ResourceKey identifier——代码名与实际名可能不同：
 * 代码 {@code Noises.SHIFT} 实际名是 {@code minecraft:offset}。</p>
 * <p>值 = {@code NormalNoise.getValue(玩家 block 坐标)}，内部含 MCRe 的 wrap/reposition/BedrockMode
 * 精度魔改，即噪声计算的最终返回值。</p>
 * <p>每个噪声独立一行（按名称排序防溢屏），位置变化才重算（噪声是位置的纯函数，不动不重算）。</p>
 */
@OnlyIn(Dist.CLIENT)
public class DebugEntryAllNoiseList implements DebugScreenEntry {
    private final List<String> result = new ArrayList<>();
    private @Nullable BlockPos lastPos = null;

    @Override
    public void display(
        final DebugScreenDisplayer displayer,
        final @Nullable Level serverOrClientLevel,
        final @Nullable LevelChunk clientChunk,
        final @Nullable LevelChunk serverChunk
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.getCameraEntity();
        ServerLevel serverLevel = serverOrClientLevel instanceof ServerLevel level ? level : null;
        if (entity != null && serverLevel != null) {
            BlockPos feetPos = entity.blockPosition();
            if (!feetPos.equals(this.lastPos)) {
                this.update(serverLevel, feetPos);
            }

            displayer.addToGroup(DebugGroups.TERRAIN_NOISES, this.result);
        }
    }

    private void update(final ServerLevel serverLevel, final BlockPos feetPos) {
        this.result.clear();
        this.lastPos = feetPos;
        ServerChunkCache chunkSource = serverLevel.getChunkSource();
        RandomState randomState = chunkSource.randomState();
        // 🔧 修复：采样坐标 = 偏移缩放后的地形坐标（Terrain XYZ）——与 NoiseRouter 的实际采样一致；
        // 玩家实际坐标直接采样会绕过偏移缩放（1e50 缩放下该显示 NaN 的位置会错误地显示 -1.5~1.5 的正常值）
        double sampleX = WorldReposition.reposition((double)feetPos.getX(), Direction.Axis.X);
        double sampleY = WorldReposition.reposition((double)feetPos.getY(), Direction.Axis.Y);
        double sampleZ = WorldReposition.reposition((double)feetPos.getZ(), Direction.Axis.Z);
        Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> noises = randomState.noiseInstances();
        List<Map.Entry<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise>> sorted = new ArrayList<>(noises.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getKey().identifier().toString()));
        for (Map.Entry<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> entry : sorted) {
            String name = entry.getKey().identifier().toString();
            double value = entry.getValue().getValue(sampleX, sampleY, sampleZ);
            // 🔧 用 Double.toString（String.valueOf）：大值自动科学记数法（1.14514191981E113），
            // 有效数字全保留、末尾无效零全部省略；NaN/Inf 显示为 NaN/Infinity；小值保持十进制
            // 🔧 方案 B 溢出监测：复刻检测器定位首个饱和的组/倍频（F3=first 第3倍频/S5=second 第5倍频），饱和标红
            String saturate = NoiseOverflowUtil.detectFirstSaturate(entry.getValue(), sampleX, sampleY, sampleZ);
            this.result.add(name + ": " + String.valueOf(value) + (saturate != null ? " §c[S@" + saturate + "]" : ""));
        }
    }
}