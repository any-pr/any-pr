package net.minecraft.client.gui.components.debug;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

public class DebugGroups {
    private static void register(final String name, final DebugGroup group) {
        BY_NAME.put(Identifier.withDefaultNamespace(name), group);
    }

    /** 26.2 旧组名 → 26.4 分组；未注册的回退 MISC */
    public static DebugGroup byName(final Identifier id) {
        return id == null ? MISC : BY_NAME.getOrDefault(id, MISC);
    }

    /** 🔧 MCRe：26.4 分组 → 稳定组名（过渡期 overlay 桥接用） */
    public static Identifier idOf(final DebugGroup group) {
        if (group == null) {
            return Identifier.withDefaultNamespace("misc");
        }
        for (Map.Entry<Identifier, DebugGroup> e : BY_NAME.entrySet()) {
            if (e.getValue() == group) {
                return e.getKey();
            }
        }
        return Identifier.withDefaultNamespace("misc");
    }
   public static final DebugGroup HELP = DebugGroup.Builder.titled("Help").build();
   public static final DebugGroup MISC = DebugGroup.Builder.titleless().build();
   public static final DebugGroup PRIORITY = DebugGroup.Builder.titleless().build();
   public static final DebugGroup LIGHT = DebugGroup.Builder.titled("Light").withAccentColor(16776960).build();
   public static final DebugGroup LOOKING_AT_BLOCK = DebugGroup.Builder.titled("Looking At Block").withAccentColor(13369599).build();
   public static final DebugGroup LOOKING_AT_FLUID = DebugGroup.Builder.titled("Looking At Fluid").withAccentColor(16763904).build();
   public static final DebugGroup LOOKING_AT_ENTITY = DebugGroup.Builder.titled("Looking At Entity").withAccentColor(65484).build();
   public static final DebugGroup MEMORY = DebugGroup.Builder.titled("Memory").withAccentColor(16751360).withPreferredColumn(DebugColumn.Side.RIGHT).build();
   public static final DebugGroup POSITION = DebugGroup.Builder.titled("Position").withAccentColor(16777215).withPreferredColumn(DebugColumn.Side.LEFT).build();
   public static final DebugGroup CHUNK_RENDERING = DebugGroup.Builder.titled("Chunk Rendering").withAccentColor(15773856).build();
   public static final DebugGroup PERFORMANCE_IMPACTORS = DebugGroup.Builder.titled("Performance Impactors")
      .withAccentColor(65280)
      .withPreferredColumn(DebugColumn.Side.RIGHT)
      .build();
   public static final DebugGroup SYSTEM_SPECS = DebugGroup.Builder.titled("System Specs")
      .withAccentColor(16711680)
      .withPreferredColumn(DebugColumn.Side.RIGHT)
      .build();
   public static final DebugGroup HEIGHTMAP = DebugGroup.Builder.titled("Heightmap").withAccentColor(43775).build();
   public static final DebugGroup CHUNK_GENERATION = DebugGroup.Builder.titled("Chunk Generation").withAccentColor(10092458).build();
   public static final DebugGroup SPAWN_COUNTS = DebugGroup.Builder.titled("Entity Spawn Counts").withAccentColor(16729156).build();
   // 🔧 MCRe：地形噪声组（DebugEntryAllNoiseList 条目用）
   public static final DebugGroup TERRAIN_NOISES = DebugGroup.Builder.titled("Terrain Noises").withAccentColor(65535).build();
   // 🔧 MCRe：密度函数监视器组（DebugEntryDensityFunctionsMonitor 条目用）
   public static final DebugGroup DENSITY_FUNCTIONS_MONITOR = DebugGroup.Builder.titled("Density Functions Monitor").withAccentColor(16737280).build();

    // 🔧 MCRe：26.2 旧组名（Identifier）→ 26.4 分组的映射（过渡期兼容用）
    private static final Map<Identifier, DebugGroup> BY_NAME = new HashMap<>();

    static {
        register("help", HELP);
        register("misc", MISC);
        register("priority", PRIORITY);
        register("light", LIGHT);
        register("looking_at_block", LOOKING_AT_BLOCK);
        register("looking_at_fluid", LOOKING_AT_FLUID);
        register("looking_at_entity", LOOKING_AT_ENTITY);
        register("memory", MEMORY);
        register("detailed_memory", MEMORY);
        register("position", POSITION);
        register("chunk_rendering", CHUNK_RENDERING);
        register("performance_impactors", PERFORMANCE_IMPACTORS);
        register("system", SYSTEM_SPECS);
        register("system_specs", SYSTEM_SPECS);
        register("heightmaps", HEIGHTMAP);
        register("heightmap", HEIGHTMAP);
        register("chunk_generation", CHUNK_GENERATION);
        register("spawn_counts", SPAWN_COUNTS);
        register("terrain_noises", TERRAIN_NOISES);
        register("density_functions_monitor", DENSITY_FUNCTIONS_MONITOR);
        // 26.2 条目分组：biome 在 26.4 并入 Position 面板（Biome: minecraft:plains 显示在 Position 内）
        register("biome", POSITION);
        register("post_effect", MISC);
        register("post_effects", MISC);
    }


}
