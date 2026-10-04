package net.minecraft.world.level;

/**
 * 🔧 MCRe NoiseFarlands：极端 Y 下界扫描的迭代上限。
 *
 * <p><b>问题</b>：超高世界（height → ±21.47 亿）下，vanilla 的「从方块 Y 一路扫到世界底部」循环
 * 会迭代 21 亿次（每次读方块 / 查 section），放置/破坏一个方块就卡死级卡顿。
 * 涉及：{@code Heightmap.update}、{@code ChunkSkyLightSources.findLowestSourceBelow}、
 * {@code SkyLightEngine.removeSourcesBelow / countEmptySectionsBelowIfAtBorder}。
 *
 * <p><b>修法</b>：钳制扫描深度。这些扫描用于找「下一个不透明方块 / 下一个光源 / 下一个有数据的 section」，
 * 正常游玩时的实际距离远小于上限（最多几十格），所以钳制不影响正常行为；
 * 极端 Y 下扫描提前结束并保守返回（该列近似无光源/无遮挡），比 21 亿次迭代好。
 */
public final class FarLandsYScan {
    /** 逐方块扫描上限（Heightmap.update / ChunkSkyLightSources.findLowestSourceBelow）。 */
    public static final int MAX_BLOCK_SCAN = 2048;

    /** 逐 section 扫描上限（SkyLightEngine 列更新），= MAX_BLOCK_SCAN / 16。 */
    public static final int MAX_SECTION_SCAN = MAX_BLOCK_SCAN / 16;

    private FarLandsYScan() {
    }
}
