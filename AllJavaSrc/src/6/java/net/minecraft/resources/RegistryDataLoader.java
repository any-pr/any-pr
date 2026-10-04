package net.minecraft.resources;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.frog.FrogVariant;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariant;
import net.minecraft.world.entity.animal.pig.PigVariant;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPreset;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.timeline.Timeline;
import org.slf4j.Logger;

public class RegistryDataLoader {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Comparator<ResourceKey<?>> ERROR_KEY_COMPARATOR = Comparator.comparing(ResourceKey::registry).thenComparing(ResourceKey::identifier);
   private static final RegistrationInfo NETWORK_REGISTRATION_INFO = new RegistrationInfo(Optional.empty(), Lifecycle.experimental());
   private static final Function<Optional<KnownPack>, RegistrationInfo> REGISTRATION_INFO_CACHE = Util.memoize(knownPack -> {
      Lifecycle lifecycle = knownPack.map(KnownPack::isVanilla).map(info -> Lifecycle.stable()).orElse(Lifecycle.experimental());
      return new RegistrationInfo(knownPack, lifecycle);
   });
   public static final List<RegistryDataLoader.RegistryData<?>> WORLDGEN_REGISTRIES = List.of(
      new RegistryDataLoader.RegistryData<>(Registries.DIMENSION_TYPE, DimensionType.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.BIOME, Biome.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.CHAT_TYPE, ChatType.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.CONFIGURED_CARVER, ConfiguredWorldCarver.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.CONFIGURED_FEATURE, ConfiguredFeature.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.PLACED_FEATURE, PlacedFeature.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.STRUCTURE, Structure.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.PROCESSOR_LIST, StructureProcessorType.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.TEMPLATE_POOL, StructureTemplatePool.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.NOISE_SETTINGS, NoiseGeneratorSettings.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.NOISE, NormalNoise.NoiseParameters.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.DENSITY_FUNCTION, DensityFunction.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.WORLD_PRESET, WorldPreset.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.FLAT_LEVEL_GENERATOR_PRESET, FlatLevelGeneratorPreset.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.TRIM_PATTERN, TrimPattern.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.TRIM_MATERIAL, TrimMaterial.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.TRIAL_SPAWNER_CONFIG, TrialSpawnerConfig.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.WOLF_VARIANT, WolfVariant.DIRECT_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.WOLF_SOUND_VARIANT, WolfSoundVariant.DIRECT_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.PIG_VARIANT, PigVariant.DIRECT_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.FROG_VARIANT, FrogVariant.DIRECT_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.CAT_VARIANT, CatVariant.DIRECT_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.COW_VARIANT, CowVariant.DIRECT_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.CHICKEN_VARIANT, ChickenVariant.DIRECT_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.ZOMBIE_NAUTILUS_VARIANT, ZombieNautilusVariant.DIRECT_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.PAINTING_VARIANT, PaintingVariant.DIRECT_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterList.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.BANNER_PATTERN, BannerPattern.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT_PROVIDER, EnchantmentProvider.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.INSTRUMENT, Instrument.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.TEST_ENVIRONMENT, TestEnvironmentDefinition.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.TEST_INSTANCE, GameTestInstance.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.DIALOG, Dialog.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.TIMELINE, Timeline.DIRECT_CODEC)
   );
   public static final List<RegistryDataLoader.RegistryData<?>> DIMENSION_REGISTRIES = List.of(
      new RegistryDataLoader.RegistryData<>(Registries.LEVEL_STEM, LevelStem.CODEC)
   );
   public static final List<RegistryDataLoader.RegistryData<?>> SYNCHRONIZED_REGISTRIES = List.of(
      new RegistryDataLoader.RegistryData<>(Registries.BIOME, Biome.NETWORK_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.CHAT_TYPE, ChatType.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.TRIM_PATTERN, TrimPattern.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.TRIM_MATERIAL, TrimMaterial.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.WOLF_VARIANT, WolfVariant.NETWORK_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.WOLF_SOUND_VARIANT, WolfSoundVariant.NETWORK_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.PIG_VARIANT, PigVariant.NETWORK_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.FROG_VARIANT, FrogVariant.NETWORK_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.CAT_VARIANT, CatVariant.NETWORK_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.COW_VARIANT, CowVariant.NETWORK_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.CHICKEN_VARIANT, ChickenVariant.NETWORK_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.ZOMBIE_NAUTILUS_VARIANT, ZombieNautilusVariant.NETWORK_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.PAINTING_VARIANT, PaintingVariant.DIRECT_CODEC, true),
      new RegistryDataLoader.RegistryData<>(Registries.DIMENSION_TYPE, DimensionType.NETWORK_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.BANNER_PATTERN, BannerPattern.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.INSTRUMENT, Instrument.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.TEST_ENVIRONMENT, TestEnvironmentDefinition.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.TEST_INSTANCE, GameTestInstance.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.DIALOG, Dialog.DIRECT_CODEC),
      new RegistryDataLoader.RegistryData<>(Registries.TIMELINE, Timeline.NETWORK_CODEC)
   );

   public static RegistryAccess.Frozen load(
      final ResourceManager resourceManager,
      final List<HolderLookup.RegistryLookup<?>> contextRegistries,
      final List<RegistryDataLoader.RegistryData<?>> registriesToLoad
   ) {
      return load((loader, context) -> loader.loadFromResources(resourceManager, context), contextRegistries, registriesToLoad);
   }

   public static RegistryAccess.Frozen load(
      final Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> entries,
      final ResourceProvider knownDataSource,
      final List<HolderLookup.RegistryLookup<?>> contextRegistries,
      final List<RegistryDataLoader.RegistryData<?>> registriesToLoad
   ) {
      return load((loader, context) -> loader.loadFromNetwork(entries, knownDataSource, context), contextRegistries, registriesToLoad);
   }

   private static RegistryAccess.Frozen load(
      final RegistryDataLoader.LoadingFunction loadingFunction,
      final List<HolderLookup.RegistryLookup<?>> contextRegistries,
      final List<RegistryDataLoader.RegistryData<?>> registriesToLoad
   ) {
      Map<ResourceKey<?>, Exception> loadingErrors = new HashMap<>();
      List<RegistryDataLoader.Loader<?>> newRegistriesAndLoaders = registriesToLoad.stream()
         .map(r -> r.create(Lifecycle.stable(), loadingErrors))
         .collect(Collectors.toUnmodifiableList());
      RegistryOps.RegistryInfoLookup contextAndNewRegistries = createContext(contextRegistries, newRegistriesAndLoaders);
      newRegistriesAndLoaders.forEach(loader -> loadingFunction.apply((RegistryDataLoader.Loader<?>)loader, contextAndNewRegistries));
      newRegistriesAndLoaders.forEach(p -> {
         Registry<?> registry = p.registry();

         try {
            registry.freeze();
         } catch (Exception e) {
            loadingErrors.put(registry.key(), e);
         }

         if (p.data.requiredNonEmpty && registry.size() == 0) {
            loadingErrors.put(registry.key(), new IllegalStateException("Registry must be non-empty: " + registry.key().identifier()));
         }
      });
      if (!loadingErrors.isEmpty()) {
         throw logErrors(loadingErrors);
      } else {
         return new RegistryAccess.ImmutableRegistryAccess(newRegistriesAndLoaders.stream().map(RegistryDataLoader.Loader::registry).toList()).freeze();
      }
   }

   private static RegistryOps.RegistryInfoLookup createContext(
      final List<HolderLookup.RegistryLookup<?>> contextRegistries, final List<RegistryDataLoader.Loader<?>> newRegistriesAndLoaders
   ) {
      final Map<ResourceKey<? extends Registry<?>>, RegistryOps.RegistryInfo<?>> result = new HashMap<>();
      contextRegistries.forEach(e -> result.put(e.key(), createInfoForContextRegistry((HolderLookup.RegistryLookup<?>)e)));
      newRegistriesAndLoaders.forEach(e -> result.put(e.registry.key(), createInfoForNewRegistry(e.registry)));
      return new RegistryOps.RegistryInfoLookup() {
         @Override
         public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(final ResourceKey<? extends Registry<? extends T>> key) {
            return Optional.ofNullable((RegistryOps.RegistryInfo<T>)result.get(key));
         }
      };
   }

   private static <T> RegistryOps.RegistryInfo<T> createInfoForNewRegistry(final WritableRegistry<T> e) {
      return new RegistryOps.RegistryInfo<>(e, e.createRegistrationLookup(), e.registryLifecycle());
   }

   private static <T> RegistryOps.RegistryInfo<T> createInfoForContextRegistry(final HolderLookup.RegistryLookup<T> lookup) {
      return new RegistryOps.RegistryInfo<>(lookup, lookup, lookup.registryLifecycle());
   }

   private static ReportedException logErrors(final Map<ResourceKey<?>, Exception> loadingErrors) {
      printFullDetailsToLog(loadingErrors);
      return createReportWithBriefInfo(loadingErrors);
   }

   private static void printFullDetailsToLog(final Map<ResourceKey<?>, Exception> loadingErrors) {
      StringWriter collectedErrors = new StringWriter();
      PrintWriter errorPrinter = new PrintWriter(collectedErrors);
      Map<Identifier, Map<Identifier, Exception>> errorsByRegistry = loadingErrors.entrySet()
         .stream()
         .collect(Collectors.groupingBy(e -> e.getKey().registry(), Collectors.toMap(e -> e.getKey().identifier(), Entry::getValue)));
      errorsByRegistry.entrySet().stream().sorted(Entry.comparingByKey()).forEach(registryEntry -> {
         errorPrinter.printf(Locale.ROOT, "> Errors in registry %s:%n", registryEntry.getKey());
         registryEntry.getValue().entrySet().stream().sorted(Entry.comparingByKey()).forEach(elementError -> {
            errorPrinter.printf(Locale.ROOT, ">> Errors in element %s:%n", elementError.getKey());
            elementError.getValue().printStackTrace(errorPrinter);
         });
      });
      errorPrinter.flush();
      LOGGER.error("Registry loading errors:\n{}", collectedErrors);
   }

   private static ReportedException createReportWithBriefInfo(final Map<ResourceKey<?>, Exception> loadingErrors) {
      CrashReport report = CrashReport.forThrowable(new IllegalStateException("Failed to load registries due to errors"), "Registry Loading");
      CrashReportCategory errors = report.addCategory("Loading info");
      errors.setDetail(
         "Errors",
         () -> {
            StringBuilder briefDetails = new StringBuilder();
            loadingErrors.entrySet()
               .stream()
               .sorted(Entry.comparingByKey(ERROR_KEY_COMPARATOR))
               .forEach(
                  e -> briefDetails.append("\n\t\t")
                     .append(e.getKey().registry())
                     .append("/")
                     .append(e.getKey().identifier())
                     .append(": ")
                     .append(e.getValue().getMessage())
               );
            return briefDetails.toString();
         }
      );
      return new ReportedException(report);
   }

   private static <E> void loadElementFromResource(
      final WritableRegistry<E> output,
      final Decoder<E> elementDecoder,
      final RegistryOps<JsonElement> ops,
      final ResourceKey<E> elementKey,
      final Resource thunk,
      final RegistrationInfo registrationInfo
   ) throws IOException {
      try (Reader reader = thunk.openAsReader()) {
         JsonElement json = StrictJsonParser.parse(reader);
         DataResult<E> parseResult = elementDecoder.parse(ops, json);
         E parsedElement = (E)parseResult.getOrThrow();
         output.register(elementKey, parsedElement, registrationInfo);
      }
   }

   private static <E> void loadContentsFromManager(
      final ResourceManager resourceManager,
      final RegistryOps.RegistryInfoLookup lookup,
      final WritableRegistry<E> registry,
      final Decoder<E> elementDecoder,
      final Map<ResourceKey<?>, Exception> loadingErrors
   ) {
      FileToIdConverter lister = FileToIdConverter.registry(registry.key());
      RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, lookup);

      for (Entry<Identifier, Resource> resourceEntry : lister.listMatchingResources(resourceManager).entrySet()) {
         Identifier resourceId = resourceEntry.getKey();
         ResourceKey<E> elementKey = ResourceKey.create(registry.key(), lister.fileToId(resourceId));
         Resource thunk = resourceEntry.getValue();
         RegistrationInfo registrationInfo = REGISTRATION_INFO_CACHE.apply(thunk.knownPackInfo());

         try {
            loadElementFromResource(registry, elementDecoder, ops, elementKey, thunk, registrationInfo);
         } catch (Exception e) {
            loadingErrors.put(
               elementKey, new IllegalStateException(String.format(Locale.ROOT, "Failed to parse %s from pack %s", resourceId, thunk.sourcePackId()), e)
            );
         }
      }

      TagLoader.loadTagsForRegistry(resourceManager, registry);
   }

   private static <E> void loadContentsFromNetwork(
      final Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> entries,
      final ResourceProvider knownDataSource,
      final RegistryOps.RegistryInfoLookup lookup,
      final WritableRegistry<E> registry,
      final Decoder<E> elementDecoder,
      final Map<ResourceKey<?>, Exception> loadingErrors
   ) {
      RegistryDataLoader.NetworkedRegistryData registryEntries = entries.get(registry.key());
      if (registryEntries != null) {
         RegistryOps<Tag> nbtOps = RegistryOps.create(NbtOps.INSTANCE, lookup);
         RegistryOps<JsonElement> jsonOps = RegistryOps.create(JsonOps.INSTANCE, lookup);
         FileToIdConverter knownDataPathConverter = FileToIdConverter.registry(registry.key());

         for (RegistrySynchronization.PackedRegistryEntry entry : registryEntries.elements) {
            ResourceKey<E> elementKey = ResourceKey.create(registry.key(), entry.id());
            Optional<Tag> data = entry.data();
            if (data.isPresent()) {
               try {
                  DataResult<E> parseResult = elementDecoder.parse(nbtOps, data.get());
                  E parsedElement = (E)parseResult.getOrThrow();
                  registry.register(elementKey, parsedElement, NETWORK_REGISTRATION_INFO);
               } catch (Exception e) {
                  loadingErrors.put(elementKey, new IllegalStateException(String.format(Locale.ROOT, "Failed to parse value %s from server", data.get()), e));
               }
            } else {
               Identifier knownDataPath = knownDataPathConverter.idToFile(entry.id());

               try {
                  Resource thunk = knownDataSource.getResourceOrThrow(knownDataPath);
                  loadElementFromResource(registry, elementDecoder, jsonOps, elementKey, thunk, NETWORK_REGISTRATION_INFO);
               } catch (Exception e) {
                  loadingErrors.put(elementKey, new IllegalStateException("Failed to parse local data", e));
               }
            }
         }

         TagLoader.loadTagsFromNetwork(registryEntries.tags, registry);
      }
   }

   private record Loader<T>(RegistryDataLoader.RegistryData<T> data, WritableRegistry<T> registry, Map<ResourceKey<?>, Exception> loadingErrors) {
      public void loadFromResources(final ResourceManager resourceManager, final RegistryOps.RegistryInfoLookup context) {
         RegistryDataLoader.loadContentsFromManager(resourceManager, context, this.registry, this.data.elementCodec, this.loadingErrors);
      }

      public void loadFromNetwork(
         final Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> entries,
         final ResourceProvider knownDataSource,
         final RegistryOps.RegistryInfoLookup context
      ) {
         RegistryDataLoader.loadContentsFromNetwork(entries, knownDataSource, context, this.registry, this.data.elementCodec, this.loadingErrors);
      }
   }

   @FunctionalInterface
   private interface LoadingFunction {
      void apply(RegistryDataLoader.Loader<?> loader, RegistryOps.RegistryInfoLookup context);
   }

   public record NetworkedRegistryData(List<RegistrySynchronization.PackedRegistryEntry> elements, TagNetworkSerialization.NetworkPayload tags) {
   }

   public record RegistryData<T>(ResourceKey<? extends Registry<T>> key, Codec<T> elementCodec, boolean requiredNonEmpty) {
      private RegistryData(final ResourceKey<? extends Registry<T>> key, final Codec<T> elementCodec) {
         this(key, elementCodec, false);
      }

      private RegistryDataLoader.Loader<T> create(final Lifecycle lifecycle, final Map<ResourceKey<?>, Exception> loadingErrors) {
         WritableRegistry<T> result = new MappedRegistry<>(this.key, lifecycle);
         return new RegistryDataLoader.Loader<>(this, result, loadingErrors);
      }

      public void runWithArguments(final BiConsumer<ResourceKey<? extends Registry<T>>, Codec<T>> output) {
         output.accept(this.key, this.elementCodec);
      }
   }
}
