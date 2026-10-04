package net.minecraft.client.gui.components.debug;

import java.util.Collection;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 🔧 MCRe：调试屏幕写入接口 —— 移植 26.4 Snapshot 2 全新架构。
 *
 * <p>26.4 的条目通过 {@link DebugGroup}（标题 + 强调色 + 首选列）写入分组面板，
 * {@link DebugFact} 提供「名字: 值」对齐显示，{@link DebugCustomRenderer} 支持 FPS 图表等自定义渲染。
 *
 * <p><b>过渡期兼容</b>：26.2 旧 API（{@link #addLine(String)} / {@link #addToGroup(Identifier, String)}）
 * 以 default 方法保留，映射到 {@link DebugGroups#MISC} / {@link DebugGroups#byName(Identifier)}，
 * 让 34 个条目可以逐批迁移而不断编译。全部迁移完成后可移除。
 *
 * @since 2026-10-01
 */
@OnlyIn(Dist.CLIENT)
public interface DebugScreenDisplayer {

    /** 优先级行（双列均分，始终置顶显示） */
    void addPriorityLine(String line);

    // ==================== 26.4 新 API ====================

    /** 写入分组的整组行 */
    void addToGroup(final DebugGroup group, Collection<String> lines);

    /** 写入分组的单行 */
    void addToGroup(final DebugGroup group, String lines);

    /** 写入分组的「名字: 值」对齐条目（builder 链式构建值） */
    void addFactToGroup(final DebugGroup group, String name, Consumer<DebugFact> builder);

    /** 写入分组的自定义渲染器（FPS 图表等） */
    void addToGroup(final DebugGroup group, DebugCustomRenderer customRenderer);

    // ==================== 26.2 旧 API（过渡期 default 兼容）====================

    /** 旧 API：单行 → MISC 组。条目迁移完成后移除。 */
    default void addLine(String line) {
        this.addToGroup(DebugGroups.MISC, line);
    }

    /** 旧 API：整组行（Identifier）→ 按名映射的 26.4 分组。条目迁移完成后移除。 */
    default void addToGroup(final Identifier group, Collection<String> lines) {
        this.addToGroup(DebugGroups.byName(group), lines);
    }

    /** 旧 API：单行（Identifier）→ 按名映射的 26.4 分组。条目迁移完成后移除。 */
    default void addToGroup(final Identifier group, String lines) {
        this.addToGroup(DebugGroups.byName(group), lines);
    }
}