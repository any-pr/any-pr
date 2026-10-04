package net.minecraft.data.info;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryOps;

public class ItemListReport implements DataProvider {
   private final PackOutput output;
   private final CompletableFuture<HolderLookup.Provider> registries;

   public ItemListReport(final PackOutput output, final CompletableFuture<HolderLookup.Provider> registries) {
      this.output = output;
      this.registries = registries;
   }

   @Override
   public CompletableFuture<?> run(final CachedOutput cache) {
      Path path = this.output.getOutputFolder(PackOutput.Target.REPORTS).resolve("items.json");
      return this.registries
         .thenCompose(
            registries -> {
               JsonObject root = new JsonObject();
               RegistryOps<JsonElement> registryOps = registries.createSerializationContext(JsonOps.INSTANCE);
               registries.lookupOrThrow(Registries.ITEM)
                  .listElements()
                  .forEach(
                     item -> {
                        JsonObject entry = new JsonObject();
                        entry.add(
                           "components",
                           (JsonElement)DataComponentMap.CODEC
                              .encodeStart(registryOps, item.value().components())
                              .getOrThrow(err -> new IllegalStateException("Failed to encode components: " + err))
                        );
                        root.add(item.getRegisteredName(), entry);
                     }
                  );
               return DataProvider.saveStable(cache, root, path);
            }
         );
   }

   @Override
   public final String getName() {
      return "Item List";
   }
}
