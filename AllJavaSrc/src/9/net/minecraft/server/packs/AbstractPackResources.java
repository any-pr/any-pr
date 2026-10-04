package net.minecraft.server.packs;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.GsonHelper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class AbstractPackResources implements PackResources {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final PackLocationInfo location;

   protected AbstractPackResources(final PackLocationInfo location) {
      this.location = location;
   }

   @Override
   public <T> @Nullable T getMetadataSection(final MetadataSectionType<T> metadataSerializer) throws IOException {
      IoSupplier<InputStream> metadata = this.getRootResource("pack.mcmeta");
      if (metadata == null) {
         return null;
      }

      try (InputStream resource = metadata.get()) {
         return getMetadataFromStream(metadataSerializer, resource, this.location);
      }
   }

   public static <T> @Nullable T getMetadataFromStream(final MetadataSectionType<T> serializer, final InputStream stream, final PackLocationInfo location) {
      JsonObject metadata;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
         metadata = GsonHelper.parse(reader);
      } catch (Exception e) {
         LOGGER.error("Couldn't load {} {} metadata: {}", new Object[]{location.id(), serializer.name(), e.getMessage()});
         return null;
      }

      return (T)(!metadata.has(serializer.name())
         ? null
         : serializer.codec()
            .parse(JsonOps.INSTANCE, metadata.get(serializer.name()))
            .ifError(error -> LOGGER.error("Couldn't load {} {} metadata: {}", new Object[]{location.id(), serializer.name(), error.message()}))
            .result()
            .orElse(null));
   }

   @Override
   public PackLocationInfo location() {
      return this.location;
   }
}
