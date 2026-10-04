package net.minecraft.data;

import java.nio.file.Path;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public class PackOutput {
   private final Path outputFolder;

   public PackOutput(final Path outputFolder) {
      this.outputFolder = outputFolder;
   }

   public Path getOutputFolder() {
      return this.outputFolder;
   }

   public Path getOutputFolder(final PackOutput.Target target) {
      return this.getOutputFolder().resolve(target.directory);
   }

   public PackOutput.PathProvider createPathProvider(final PackOutput.Target target, final String kind) {
      return new PackOutput.PathProvider(this, target, kind);
   }

   public PackOutput.PathProvider createRegistryElementsPathProvider(final ResourceKey<? extends Registry<?>> registryKey) {
      return this.createPathProvider(PackOutput.Target.DATA_PACK, Registries.elementsDirPath(registryKey));
   }

   public PackOutput.PathProvider createRegistryTagsPathProvider(final ResourceKey<? extends Registry<?>> registryKey) {
      return this.createPathProvider(PackOutput.Target.DATA_PACK, Registries.tagsDirPath(registryKey));
   }

   public static class PathProvider {
      private final Path root;
      private final String kind;

      private PathProvider(final PackOutput output, final PackOutput.Target target, final String kind) {
         this.root = output.getOutputFolder(target);
         this.kind = kind;
      }

      public Path file(final Identifier element, final String extension) {
         return this.root.resolve(element.getNamespace()).resolve(this.kind).resolve(element.getPath() + "." + extension);
      }

      public Path json(final Identifier element) {
         return this.root.resolve(element.getNamespace()).resolve(this.kind).resolve(element.getPath() + ".json");
      }

      public Path json(final ResourceKey<?> element) {
         return this.root.resolve(element.identifier().getNamespace()).resolve(this.kind).resolve(element.identifier().getPath() + ".json");
      }
   }

   public enum Target {
      DATA_PACK("data"),
      RESOURCE_PACK("assets"),
      REPORTS("reports");

      private final String directory;

      Target(final String directory) {
         this.directory = directory;
      }
   }
}
