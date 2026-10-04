package net.minecraft.client.gui.components.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

/**
 * 🔧 MCRe：Chunk Source Stats 条目 —— 26.4 分组架构（{@link DebugGroups#PERFORMANCE_IMPACTORS}，绿强调色）。
 * 26.4 反编译版依赖 {@code getEntityStorage()}/{@code sectionCount()}/{@code tickingCount()}/
 * {@code getEntityManager()}（26.2 无这些访问器），此处用 26.2 确认存在的 API 适配：
 * {@code getChunkSource().getLoadedChunksCount()} ✓ + {@code gatherChunkSourceStats()} ✓。
 */
@OnlyIn(Dist.CLIENT)
public class DebugEntryChunkSourceStats implements DebugScreenEntry {
    @Override
    public void display(
        final DebugScreenDisplayer displayer,
        final @Nullable Level serverOrClientLevel,
        final @Nullable LevelChunk clientChunk,
        final @Nullable LevelChunk serverChunk
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            // 26.4 Fact 风格：Client Chunks（名字右对齐 + 值）
            displayer.addFactToGroup(
                DebugGroups.PERFORMANCE_IMPACTORS,
                "Client Chunks",
                fact -> fact.value(minecraft.level.getChunkSource().getLoadedChunksCount()).text(" loaded")
            );
            // 26.4 的 getEntityStorage()/sectionCount()/tickingCount() 26.2 无访问器 → 聚合统计代替
            displayer.addToGroup(DebugGroups.PERFORMANCE_IMPACTORS, minecraft.level.gatherChunkSourceStats());
        }

        if (serverOrClientLevel instanceof ServerLevel) {
            displayer.addFactToGroup(
                DebugGroups.PERFORMANCE_IMPACTORS,
                "Server Chunks",
                fact -> fact.value(serverOrClientLevel.getChunkSource().getLoadedChunksCount()).value(" loaded")
            );
            // 26.4 的 getEntityManager().gatherStats() 26.2 无访问器 → 聚合统计代替
            displayer.addToGroup(DebugGroups.PERFORMANCE_IMPACTORS, serverOrClientLevel.gatherChunkSourceStats());
        }
    }

    @Override
    public boolean isAllowed(final boolean reducedDebugInfo) {
        return true;
    }
}