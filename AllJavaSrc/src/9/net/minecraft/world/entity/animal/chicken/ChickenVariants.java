package net.minecraft.world.entity.animal.chicken;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.TemperatureVariants;
import net.minecraft.world.entity.variant.BiomeCheck;
import net.minecraft.world.entity.variant.ModelAndTexture;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;
import net.minecraft.world.level.biome.Biome;

public class ChickenVariants {
   public static final ResourceKey<ChickenVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
   public static final ResourceKey<ChickenVariant> WARM = createKey(TemperatureVariants.WARM);
   public static final ResourceKey<ChickenVariant> COLD = createKey(TemperatureVariants.COLD);
   public static final ResourceKey<ChickenVariant> DEFAULT = TEMPERATE;

   private static ResourceKey<ChickenVariant> createKey(final Identifier id) {
      return ResourceKey.create(Registries.CHICKEN_VARIANT, id);
   }

   public static void bootstrap(final BootstrapContext<ChickenVariant> context) {
      register(context, TEMPERATE, ChickenVariant.ModelType.NORMAL, "temperate_chicken", SpawnPrioritySelectors.fallback(0));
      register(context, WARM, ChickenVariant.ModelType.NORMAL, "warm_chicken", BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS);
      register(context, COLD, ChickenVariant.ModelType.COLD, "cold_chicken", BiomeTags.SPAWNS_COLD_VARIANT_FARM_ANIMALS);
   }

   private static void register(
      final BootstrapContext<ChickenVariant> context,
      final ResourceKey<ChickenVariant> name,
      final ChickenVariant.ModelType modelType,
      final String textureName,
      final TagKey<Biome> spawnBiome
   ) {
      HolderSet<Biome> biomes = context.lookup(Registries.BIOME).getOrThrow(spawnBiome);
      register(context, name, modelType, textureName, SpawnPrioritySelectors.single(new BiomeCheck(biomes), 1));
   }

   private static void register(
      final BootstrapContext<ChickenVariant> context,
      final ResourceKey<ChickenVariant> name,
      final ChickenVariant.ModelType modelType,
      final String textureName,
      final SpawnPrioritySelectors selectors
   ) {
      Identifier textureId = Identifier.withDefaultNamespace("entity/chicken/" + textureName);
      context.register(name, new ChickenVariant(new ModelAndTexture<>(modelType, textureId), selectors));
   }
}
