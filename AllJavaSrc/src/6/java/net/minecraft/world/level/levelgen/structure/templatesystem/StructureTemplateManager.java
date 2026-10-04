package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.IdentifierException;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.structures.NbtToSnbt;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.FileUtil;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class StructureTemplateManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final String STRUCTURE_RESOURCE_DIRECTORY_NAME = "structure";
   private static final String STRUCTURE_GENERATED_DIRECTORY_NAME = "structures";
   private static final String STRUCTURE_FILE_EXTENSION = ".nbt";
   private static final String STRUCTURE_TEXT_FILE_EXTENSION = ".snbt";
   private final Map<Identifier, Optional<StructureTemplate>> structureRepository = Maps.newConcurrentMap();
   private final DataFixer fixerUpper;
   private ResourceManager resourceManager;
   private final Path generatedDir;
   private final List<StructureTemplateManager.Source> sources;
   private final HolderGetter<Block> blockLookup;
   private static final FileToIdConverter RESOURCE_LISTER = new FileToIdConverter("structure", ".nbt");

   public StructureTemplateManager(
      final ResourceManager resourceManager,
      final LevelStorageSource.LevelStorageAccess storage,
      final DataFixer fixerUpper,
      final HolderGetter<Block> blockLookup
   ) {
      this.resourceManager = resourceManager;
      this.fixerUpper = fixerUpper;
      this.generatedDir = storage.getLevelPath(LevelResource.GENERATED_DIR).normalize();
      this.blockLookup = blockLookup;
      Builder<StructureTemplateManager.Source> builder = ImmutableList.builder();
      builder.add(new StructureTemplateManager.Source(this::loadFromGenerated, this::listGenerated));
      if (SharedConstants.IS_RUNNING_IN_IDE) {
         builder.add(new StructureTemplateManager.Source(this::loadFromTestStructures, this::listTestStructures));
      }

      builder.add(new StructureTemplateManager.Source(this::loadFromResource, this::listResources));
      this.sources = builder.build();
   }

   public StructureTemplate getOrCreate(final Identifier id) {
      Optional<StructureTemplate> cachedTemplate = this.get(id);
      if (cachedTemplate.isPresent()) {
         return cachedTemplate.get();
      }

      StructureTemplate template = new StructureTemplate();
      this.structureRepository.put(id, Optional.of(template));
      return template;
   }

   public Optional<StructureTemplate> get(final Identifier id) {
      return this.structureRepository.computeIfAbsent(id, this::tryLoad);
   }

   public Stream<Identifier> listTemplates() {
      return this.sources.stream().flatMap(s -> s.lister().get()).distinct();
   }

   private Optional<StructureTemplate> tryLoad(final Identifier id) {
      for (StructureTemplateManager.Source source : this.sources) {
         try {
            Optional<StructureTemplate> loaded = source.loader().apply(id);
            if (loaded.isPresent()) {
               return loaded;
            }
         } catch (Exception var5) {
         }
      }

      return Optional.empty();
   }

   public void onResourceManagerReload(final ResourceManager resourceManager) {
      this.resourceManager = resourceManager;
      this.structureRepository.clear();
   }

   private Optional<StructureTemplate> loadFromResource(final Identifier id) {
      Identifier identifier = RESOURCE_LISTER.idToFile(id);
      return this.load(() -> this.resourceManager.open(identifier), e -> LOGGER.error("Couldn't load structure {}", id, e));
   }

   private Stream<Identifier> listResources() {
      return RESOURCE_LISTER.listMatchingResources(this.resourceManager).keySet().stream().map(RESOURCE_LISTER::fileToId);
   }

   private Optional<StructureTemplate> loadFromTestStructures(final Identifier id) {
      return this.loadFromSnbt(id, StructureUtils.testStructuresDir);
   }

   private Stream<Identifier> listTestStructures() {
      if (!Files.isDirectory(StructureUtils.testStructuresDir)) {
         return Stream.empty();
      }

      List<Identifier> result = new ArrayList<>();
      this.listFolderContents(StructureUtils.testStructuresDir, "minecraft", ".snbt", result::add);
      return result.stream();
   }

   private Optional<StructureTemplate> loadFromGenerated(final Identifier id) {
      if (!Files.isDirectory(this.generatedDir)) {
         return Optional.empty();
      }

      Path file = this.createAndValidatePathToGeneratedStructure(id, ".nbt");
      return this.load(() -> new FileInputStream(file.toFile()), e -> LOGGER.error("Couldn't load structure from {}", file, e));
   }

   private Stream<Identifier> listGenerated() {
      if (!Files.isDirectory(this.generatedDir)) {
         return Stream.empty();
      }

      try {
         List<Identifier> result = new ArrayList<>();

         try (DirectoryStream<Path> contents = Files.newDirectoryStream(this.generatedDir, x$0 -> Files.isDirectory(x$0))) {
            for (Path namespaceDir : contents) {
               String namespace = namespaceDir.getFileName().toString();
               Path structureDir = namespaceDir.resolve("structures");
               this.listFolderContents(structureDir, namespace, ".nbt", result::add);
            }
         }

         return result.stream();
      } catch (IOException e) {
         return Stream.empty();
      }
   }

   private void listFolderContents(final Path folder, final String namespace, final String extension, final Consumer<Identifier> output) {
      int extensionLength = extension.length();
      Function<String, String> pathProcessor = s -> s.substring(0, s.length() - extensionLength);

      try (Stream<Path> contents = Files.find(
            folder, Integer.MAX_VALUE, (path, attributes) -> attributes.isRegularFile() && path.toString().endsWith(extension)
         )) {
         contents.forEach(file -> {
            try {
               output.accept(Identifier.fromNamespaceAndPath(namespace, pathProcessor.apply(this.relativize(folder, file))));
            } catch (IdentifierException e) {
               LOGGER.error("Invalid location while listing folder {} contents", folder, ex);
            }
         });
      } catch (IOException e) {
         LOGGER.error("Failed to list folder {} contents", folder, e);
      }
   }

   private String relativize(final Path root, final Path file) {
      return root.relativize(file).toString().replace(File.separator, "/");
   }

   private Optional<StructureTemplate> loadFromSnbt(final Identifier id, final Path dir) {
      if (!Files.isDirectory(dir)) {
         return Optional.empty();
      }

      Path file = FileUtil.createPathToResource(dir, id.getPath(), ".snbt");

      try (BufferedReader reader = Files.newBufferedReader(file)) {
         String input = IOUtils.toString(reader);
         return Optional.of(this.readStructure(NbtUtils.snbtToStructure(input)));
      } catch (NoSuchFileException e) {
         return Optional.empty();
      } catch (IOException | CommandSyntaxException e) {
         LOGGER.error("Couldn't load structure from {}", file, e);
         return Optional.empty();
      }
   }

   private Optional<StructureTemplate> load(final StructureTemplateManager.InputStreamOpener opener, final Consumer<Throwable> onError) {
      try (
         InputStream rawInput = opener.open();
         InputStream input = new FastBufferedInputStream(rawInput);
      ) {
         return Optional.of(this.readStructure(input));
      } catch (FileNotFoundException e) {
         return Optional.empty();
      } catch (Throwable e) {
         onError.accept(e);
         return Optional.empty();
      }
   }

   private StructureTemplate readStructure(final InputStream input) throws IOException {
      CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
      return this.readStructure(tag);
   }

   public StructureTemplate readStructure(final CompoundTag tag) {
      StructureTemplate structureTemplate = new StructureTemplate();
      int version = NbtUtils.getDataVersion(tag, 500);
      structureTemplate.load(this.blockLookup, DataFixTypes.STRUCTURE.updateToCurrentVersion(this.fixerUpper, tag, version));
      return structureTemplate;
   }

   public boolean save(final Identifier id) {
      Optional<StructureTemplate> maybeStructureTemplate = this.structureRepository.get(id);
      if (maybeStructureTemplate.isEmpty()) {
         return false;
      }

      StructureTemplate structureTemplate = maybeStructureTemplate.get();
      Path file = this.createAndValidatePathToGeneratedStructure(id, SharedConstants.DEBUG_SAVE_STRUCTURES_AS_SNBT ? ".snbt" : ".nbt");
      Path parent = file.getParent();
      if (parent == null) {
         return false;
      }

      try {
         Files.createDirectories(Files.exists(parent) ? parent.toRealPath() : parent);
      } catch (IOException e) {
         LOGGER.error("Failed to create parent directory: {}", parent);
         return false;
      }

      CompoundTag tag = structureTemplate.save(new CompoundTag());
      if (SharedConstants.DEBUG_SAVE_STRUCTURES_AS_SNBT) {
         try {
            NbtToSnbt.writeSnbt(CachedOutput.NO_CACHE, file, NbtUtils.structureToSnbt(tag));
         } catch (Throwable ignored) {
            return false;
         }
      } else {
         try (OutputStream output = new FileOutputStream(file.toFile())) {
            NbtIo.writeCompressed(tag, output);
         } catch (Throwable ignored) {
            return false;
         }
      }

      return true;
   }

   public Path createAndValidatePathToGeneratedStructure(final Identifier id, final String extension) {
      if (id.getPath().contains("//")) {
         throw new IdentifierException("Invalid resource path: " + id);
      }

      try {
         Path namespaceDir = this.generatedDir.resolve(id.getNamespace());
         Path structureDir = namespaceDir.resolve("structures");
         Path pathToResource = FileUtil.createPathToResource(structureDir, id.getPath(), extension);
         if (pathToResource.startsWith(this.generatedDir) && FileUtil.isPathNormalized(pathToResource) && FileUtil.isPathPortable(pathToResource)) {
            return pathToResource;
         } else {
            throw new IdentifierException("Invalid resource path: " + pathToResource);
         }
      } catch (InvalidPathException e) {
         throw new IdentifierException("Invalid resource path: " + id, e);
      }
   }

   public void remove(final Identifier id) {
      this.structureRepository.remove(id);
   }

   @FunctionalInterface
   private interface InputStreamOpener {
      InputStream open() throws IOException;
   }

   private record Source(Function<Identifier, Optional<StructureTemplate>> loader, Supplier<Stream<Identifier>> lister) {
   }
}
