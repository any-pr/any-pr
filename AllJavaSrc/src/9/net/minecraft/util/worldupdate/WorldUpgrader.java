package net.minecraft.util.worldupdate;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Reference2FloatMap;
import it.unimi.dsi.fastutil.objects.Reference2FloatMaps;
import it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.LegacyTagFixer;
import net.minecraft.world.level.chunk.storage.RecreatingSimpleRegionStorage;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class WorldUpgrader implements AutoCloseable {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final ThreadFactory THREAD_FACTORY = new ThreadFactoryBuilder().setDaemon(true).build();
   private static final String NEW_DIRECTORY_PREFIX = "new_";
   private static final Component STATUS_UPGRADING_POI = Component.translatable("optimizeWorld.stage.upgrading.poi");
   private static final Component STATUS_FINISHED_POI = Component.translatable("optimizeWorld.stage.finished.poi");
   private static final Component STATUS_UPGRADING_ENTITIES = Component.translatable("optimizeWorld.stage.upgrading.entities");
   private static final Component STATUS_FINISHED_ENTITIES = Component.translatable("optimizeWorld.stage.finished.entities");
   private static final Component STATUS_UPGRADING_CHUNKS = Component.translatable("optimizeWorld.stage.upgrading.chunks");
   private static final Component STATUS_FINISHED_CHUNKS = Component.translatable("optimizeWorld.stage.finished.chunks");
   private final Registry<LevelStem> dimensions;
   private final Set<ResourceKey<Level>> levels;
   private final boolean eraseCache;
   private final boolean recreateRegionFiles;
   private final LevelStorageSource.LevelStorageAccess levelStorage;
   private final Thread thread;
   private final DataFixer dataFixer;
   private volatile boolean running = true;
   private volatile boolean finished;
   private volatile float progress;
   private volatile int totalChunks;
   private volatile int totalFiles;
   private volatile int converted;
   private volatile int skipped;
   private final Reference2FloatMap<ResourceKey<Level>> progressMap = Reference2FloatMaps.synchronize(new Reference2FloatOpenHashMap());
   private volatile Component status = Component.translatable("optimizeWorld.stage.counting");
   private static final Pattern REGEX = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");
   private final DimensionDataStorage overworldDataStorage;

   public WorldUpgrader(
      final LevelStorageSource.LevelStorageAccess levelSource,
      final DataFixer dataFixer,
      final WorldData worldData,
      final RegistryAccess registryAccess,
      final boolean eraseCache,
      final boolean recreateRegionFiles
   ) {
      this.dimensions = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);
      this.levels = this.dimensions.registryKeySet().stream().map(Registries::levelStemToLevel).collect(Collectors.toUnmodifiableSet());
      this.eraseCache = eraseCache;
      this.dataFixer = dataFixer;
      this.levelStorage = levelSource;
      this.overworldDataStorage = new DimensionDataStorage(this.levelStorage.getDimensionPath(Level.OVERWORLD).resolve("data"), dataFixer, registryAccess);
      this.recreateRegionFiles = recreateRegionFiles;
      this.thread = THREAD_FACTORY.newThread(this::work);
      this.thread.setUncaughtExceptionHandler((t, e) -> {
         LOGGER.error("Error upgrading world", e);
         this.status = Component.translatable("optimizeWorld.stage.failed");
         this.finished = true;
      });
      this.thread.start();
   }

   public void cancel() {
      this.running = false;

      try {
         this.thread.join();
      } catch (InterruptedException var2) {
      }
   }

   private void work() {
      long conversionTime = Util.getMillis();
      LOGGER.info("Upgrading entities");
      new WorldUpgrader.EntityUpgrader().upgrade();
      LOGGER.info("Upgrading POIs");
      new WorldUpgrader.PoiUpgrader().upgrade();
      LOGGER.info("Upgrading blocks");
      new WorldUpgrader.ChunkUpgrader().upgrade();
      this.overworldDataStorage.saveAndJoin();
      conversionTime = Util.getMillis() - conversionTime;
      LOGGER.info("World optimizaton finished after {} seconds", conversionTime / 1000L);
      this.finished = true;
   }

   public boolean isFinished() {
      return this.finished;
   }

   public Set<ResourceKey<Level>> levels() {
      return this.levels;
   }

   public float dimensionProgress(final ResourceKey<Level> dimension) {
      return this.progressMap.getFloat(dimension);
   }

   public float getProgress() {
      return this.progress;
   }

   public int getTotalChunks() {
      return this.totalChunks;
   }

   public int getConverted() {
      return this.converted;
   }

   public int getSkipped() {
      return this.skipped;
   }

   public Component getStatus() {
      return this.status;
   }

   @Override
   public void close() {
      this.overworldDataStorage.close();
   }

   private static Path resolveRecreateDirectory(final Path directoryPath) {
      return directoryPath.resolveSibling("new_" + directoryPath.getFileName().toString());
   }

   private abstract class AbstractUpgrader {
      private final Component upgradingStatus;
      private final Component finishedStatus;
      private final String type;
      private final String folderName;
      protected @Nullable CompletableFuture<Void> previousWriteFuture;
      protected final DataFixTypes dataFixType;

      private AbstractUpgrader(
         final DataFixTypes dataFixType, final String type, final String folderName, final Component upgradingStatus, final Component finishedStatus
      ) {
         this.dataFixType = dataFixType;
         this.type = type;
         this.folderName = folderName;
         this.upgradingStatus = upgradingStatus;
         this.finishedStatus = finishedStatus;
      }

      public void upgrade() {
         WorldUpgrader.this.totalFiles = 0;
         WorldUpgrader.this.totalChunks = 0;
         WorldUpgrader.this.converted = 0;
         WorldUpgrader.this.skipped = 0;
         List<WorldUpgrader.DimensionToUpgrade> dimensionsToUpgrade = this.getDimensionsToUpgrade();
         if (WorldUpgrader.this.totalChunks != 0) {
            float totalSize = WorldUpgrader.this.totalFiles;
            WorldUpgrader.this.status = this.upgradingStatus;

            while (WorldUpgrader.this.running) {
               boolean worked = false;
               float totalProgress = 0.0F;

               for (WorldUpgrader.DimensionToUpgrade dimensionToUpgrade : dimensionsToUpgrade) {
                  ResourceKey<Level> dimensionKey = dimensionToUpgrade.dimensionKey;
                  ListIterator<WorldUpgrader.FileToUpgrade> iterator = dimensionToUpgrade.files;
                  SimpleRegionStorage storage = dimensionToUpgrade.storage;
                  if (iterator.hasNext()) {
                     WorldUpgrader.FileToUpgrade fileToUpgrade = iterator.next();
                     boolean converted = true;

                     for (ChunkPos chunkPos : fileToUpgrade.chunksToUpgrade) {
                        converted = converted && this.processOnePosition(dimensionKey, storage, chunkPos);
                        worked = true;
                     }

                     if (WorldUpgrader.this.recreateRegionFiles) {
                        if (converted) {
                           this.onFileFinished(fileToUpgrade.file);
                        } else {
                           WorldUpgrader.LOGGER.error("Failed to convert region file {}", fileToUpgrade.file.getPath());
                        }
                     }
                  }

                  float currentProgress = iterator.nextIndex() / totalSize;
                  WorldUpgrader.this.progressMap.put(dimensionKey, currentProgress);
                  totalProgress += currentProgress;
               }

               WorldUpgrader.this.progress = totalProgress;
               if (!worked) {
                  break;
               }
            }

            WorldUpgrader.this.status = this.finishedStatus;

            for (WorldUpgrader.DimensionToUpgrade dimensionToUpgrade : dimensionsToUpgrade) {
               try {
                  dimensionToUpgrade.storage.close();
               } catch (Exception e) {
                  WorldUpgrader.LOGGER.error("Error upgrading chunk", e);
               }
            }
         }
      }

      private List<WorldUpgrader.DimensionToUpgrade> getDimensionsToUpgrade() {
         List<WorldUpgrader.DimensionToUpgrade> dimensionsToUpgrade = Lists.newArrayList();

         for (ResourceKey<Level> dimensionKey : WorldUpgrader.this.levels) {
            RegionStorageInfo info = new RegionStorageInfo(WorldUpgrader.this.levelStorage.getLevelId(), dimensionKey, this.type);
            Path regionFolder = WorldUpgrader.this.levelStorage.getDimensionPath(dimensionKey).resolve(this.folderName);
            SimpleRegionStorage storage = this.createStorage(info, regionFolder);
            ListIterator<WorldUpgrader.FileToUpgrade> files = this.getFilesToProcess(info, regionFolder);
            dimensionsToUpgrade.add(new WorldUpgrader.DimensionToUpgrade(dimensionKey, storage, files));
         }

         return dimensionsToUpgrade;
      }

      protected abstract SimpleRegionStorage createStorage(RegionStorageInfo info, Path regionFolder);

      private ListIterator<WorldUpgrader.FileToUpgrade> getFilesToProcess(final RegionStorageInfo info, final Path regionFolder) {
         List<WorldUpgrader.FileToUpgrade> filesToUpgrade = getAllChunkPositions(info, regionFolder);
         WorldUpgrader.this.totalFiles = WorldUpgrader.this.totalFiles + filesToUpgrade.size();
         WorldUpgrader.this.totalChunks = WorldUpgrader.this.totalChunks
            + filesToUpgrade.stream().mapToInt(fileToUpgrade -> fileToUpgrade.chunksToUpgrade.size()).sum();
         return filesToUpgrade.listIterator();
      }

      private static List<WorldUpgrader.FileToUpgrade> getAllChunkPositions(final RegionStorageInfo info, final Path regionFolder) {
         File[] files = regionFolder.toFile().listFiles((dir, name) -> name.endsWith(".mca"));
         if (files == null) {
            return List.of();
         }

         List<WorldUpgrader.FileToUpgrade> regionFileChunks = Lists.newArrayList();

         for (File regionFile : files) {
            Matcher regex = WorldUpgrader.REGEX.matcher(regionFile.getName());
            if (regex.matches()) {
               int xOffset = Integer.parseInt(regex.group(1)) << 5;
               int zOffset = Integer.parseInt(regex.group(2)) << 5;
               List<ChunkPos> chunkPositions = Lists.newArrayList();

               try (RegionFile regionSource = new RegionFile(info, regionFile.toPath(), regionFolder, true)) {
                  for (int x = 0; x < 32; x++) {
                     for (int z = 0; z < 32; z++) {
                        ChunkPos pos = new ChunkPos(x + xOffset, z + zOffset);
                        if (regionSource.doesChunkExist(pos)) {
                           chunkPositions.add(pos);
                        }
                     }
                  }

                  if (!chunkPositions.isEmpty()) {
                     regionFileChunks.add(new WorldUpgrader.FileToUpgrade(regionSource, chunkPositions));
                  }
               } catch (Throwable t) {
                  WorldUpgrader.LOGGER.error("Failed to read chunks from region file {}", regionFile.toPath(), t);
               }
            }
         }

         return regionFileChunks;
      }

      private boolean processOnePosition(final ResourceKey<Level> dimension, final SimpleRegionStorage storage, final ChunkPos pos) {
         boolean converted = false;

         try {
            converted = this.tryProcessOnePosition(storage, pos, dimension);
         } catch (ReportedException | CompletionException e) {
            Throwable cause = e.getCause();
            if (!(cause instanceof IOException)) {
               throw e;
            }

            WorldUpgrader.LOGGER.error("Error upgrading chunk {}", pos, cause);
         }

         if (converted) {
            WorldUpgrader.this.converted++;
         } else {
            WorldUpgrader.this.skipped++;
         }

         return converted;
      }

      protected abstract boolean tryProcessOnePosition(final SimpleRegionStorage storage, final ChunkPos pos, final ResourceKey<Level> dimension);

      private void onFileFinished(final RegionFile regionFile) {
         if (WorldUpgrader.this.recreateRegionFiles) {
            if (this.previousWriteFuture != null) {
               this.previousWriteFuture.join();
            }

            Path filePath = regionFile.getPath();
            Path directoryPath = filePath.getParent();
            Path newFilePath = WorldUpgrader.resolveRecreateDirectory(directoryPath).resolve(filePath.getFileName().toString());

            try {
               if (newFilePath.toFile().exists()) {
                  Files.delete(filePath);
                  Files.move(newFilePath, filePath);
               } else {
                  WorldUpgrader.LOGGER.error("Failed to replace an old region file. New file {} does not exist.", newFilePath);
               }
            } catch (IOException e) {
               WorldUpgrader.LOGGER.error("Failed to replace an old region file", e);
            }
         }
      }
   }

   private class ChunkUpgrader extends WorldUpgrader.AbstractUpgrader {
      private ChunkUpgrader() {
         super(DataFixTypes.CHUNK, "chunk", "region", WorldUpgrader.STATUS_UPGRADING_CHUNKS, WorldUpgrader.STATUS_FINISHED_CHUNKS);
      }

      @Override
      protected boolean tryProcessOnePosition(final SimpleRegionStorage storage, final ChunkPos pos, final ResourceKey<Level> dimension) {
         CompoundTag chunkTag = storage.read(pos).join().orElse(null);
         if (chunkTag != null) {
            int version = NbtUtils.getDataVersion(chunkTag);
            ChunkGenerator generator = WorldUpgrader.this.dimensions.getValueOrThrow(Registries.levelToLevelStem(dimension)).generator();
            CompoundTag upgradedTag = storage.upgradeChunkTag(chunkTag, -1, ChunkMap.getChunkDataFixContextTag(dimension, generator.getTypeNameForDataFixer()));
            ChunkPos storedPos = new ChunkPos(upgradedTag.getIntOr("xPos", 0), upgradedTag.getIntOr("zPos", 0));
            if (!storedPos.equals(pos)) {
               WorldUpgrader.LOGGER.warn("Chunk {} has invalid position {}", pos, storedPos);
            }

            boolean changed = version < SharedConstants.getCurrentVersion().dataVersion().version();
            if (WorldUpgrader.this.eraseCache) {
               changed = changed || upgradedTag.contains("Heightmaps");
               upgradedTag.remove("Heightmaps");
               changed = changed || upgradedTag.contains("isLightOn");
               upgradedTag.remove("isLightOn");
               ListTag sections = upgradedTag.getListOrEmpty("sections");

               for (int i = 0; i < sections.size(); i++) {
                  Optional<CompoundTag> maybeSection = sections.getCompound(i);
                  if (!maybeSection.isEmpty()) {
                     CompoundTag section = maybeSection.get();
                     changed = changed || section.contains("BlockLight");
                     section.remove("BlockLight");
                     changed = changed || section.contains("SkyLight");
                     section.remove("SkyLight");
                  }
               }
            }

            if (changed || WorldUpgrader.this.recreateRegionFiles) {
               if (this.previousWriteFuture != null) {
                  this.previousWriteFuture.join();
               }

               this.previousWriteFuture = storage.write(pos, upgradedTag);
               return true;
            }
         }

         return false;
      }

      @Override
      protected SimpleRegionStorage createStorage(final RegionStorageInfo info, final Path regionFolder) {
         Supplier<LegacyTagFixer> legacyFixer = LegacyStructureDataHandler.getLegacyTagFixer(
            info.dimension(), () -> WorldUpgrader.this.overworldDataStorage, WorldUpgrader.this.dataFixer
         );
         return WorldUpgrader.this.recreateRegionFiles
            ? new RecreatingSimpleRegionStorage(
               info.withTypeSuffix("source"),
               regionFolder,
               info.withTypeSuffix("target"),
               WorldUpgrader.resolveRecreateDirectory(regionFolder),
               WorldUpgrader.this.dataFixer,
               true,
               DataFixTypes.CHUNK,
               legacyFixer
            )
            : new SimpleRegionStorage(info, regionFolder, WorldUpgrader.this.dataFixer, true, DataFixTypes.CHUNK, legacyFixer);
      }
   }

   record DimensionToUpgrade(ResourceKey<Level> dimensionKey, SimpleRegionStorage storage, ListIterator<WorldUpgrader.FileToUpgrade> files) {
   }

   private class EntityUpgrader extends WorldUpgrader.SimpleRegionStorageUpgrader {
      private EntityUpgrader() {
         super(DataFixTypes.ENTITY_CHUNK, "entities", WorldUpgrader.STATUS_UPGRADING_ENTITIES, WorldUpgrader.STATUS_FINISHED_ENTITIES);
      }

      @Override
      protected CompoundTag upgradeTag(final SimpleRegionStorage storage, final CompoundTag chunkTag) {
         return storage.upgradeChunkTag(chunkTag, -1);
      }
   }

   record FileToUpgrade(RegionFile file, List<ChunkPos> chunksToUpgrade) {
   }

   private class PoiUpgrader extends WorldUpgrader.SimpleRegionStorageUpgrader {
      private PoiUpgrader() {
         super(DataFixTypes.POI_CHUNK, "poi", WorldUpgrader.STATUS_UPGRADING_POI, WorldUpgrader.STATUS_FINISHED_POI);
      }

      @Override
      protected CompoundTag upgradeTag(final SimpleRegionStorage storage, final CompoundTag chunkTag) {
         return storage.upgradeChunkTag(chunkTag, 1945);
      }
   }

   private abstract class SimpleRegionStorageUpgrader extends WorldUpgrader.AbstractUpgrader {
      private SimpleRegionStorageUpgrader(final DataFixTypes type, final String folderName, final Component upgradingStatus, final Component finishedStatus) {
         super(type, folderName, folderName, upgradingStatus, finishedStatus);
      }

      @Override
      protected SimpleRegionStorage createStorage(final RegionStorageInfo info, final Path regionFolder) {
         return WorldUpgrader.this.recreateRegionFiles
            ? new RecreatingSimpleRegionStorage(
               info.withTypeSuffix("source"),
               regionFolder,
               info.withTypeSuffix("target"),
               WorldUpgrader.resolveRecreateDirectory(regionFolder),
               WorldUpgrader.this.dataFixer,
               true,
               this.dataFixType,
               LegacyTagFixer.EMPTY
            )
            : new SimpleRegionStorage(info, regionFolder, WorldUpgrader.this.dataFixer, true, this.dataFixType);
      }

      @Override
      protected boolean tryProcessOnePosition(final SimpleRegionStorage storage, final ChunkPos pos, final ResourceKey<Level> dimension) {
         CompoundTag chunkTag = storage.read(pos).join().orElse(null);
         if (chunkTag != null) {
            int version = NbtUtils.getDataVersion(chunkTag);
            CompoundTag upgradedTag = this.upgradeTag(storage, chunkTag);
            boolean changed = version < SharedConstants.getCurrentVersion().dataVersion().version();
            if (changed || WorldUpgrader.this.recreateRegionFiles) {
               if (this.previousWriteFuture != null) {
                  this.previousWriteFuture.join();
               }

               this.previousWriteFuture = storage.write(pos, upgradedTag);
               return true;
            }
         }

         return false;
      }

      protected abstract CompoundTag upgradeTag(final SimpleRegionStorage storage, final CompoundTag chunkTag);
   }
}
