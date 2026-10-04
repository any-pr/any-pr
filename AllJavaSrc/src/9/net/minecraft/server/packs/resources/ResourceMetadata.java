package net.minecraft.server.packs.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.GsonHelper;

public interface ResourceMetadata {
   ResourceMetadata EMPTY = new ResourceMetadata() {
      @Override
      public <T> Optional<T> getSection(final MetadataSectionType<T> serializer) {
         return Optional.empty();
      }
   };
   IoSupplier<ResourceMetadata> EMPTY_SUPPLIER = () -> EMPTY;

   static ResourceMetadata fromJsonStream(final InputStream inputStream) throws IOException {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
         final JsonObject metadata = GsonHelper.parse(reader);
         return new ResourceMetadata() {
            @Override
            public <T> Optional<T> getSection(final MetadataSectionType<T> serializer) {
               String name = serializer.name();
               if (metadata.has(name)) {
                  T section = (T)serializer.codec().parse(JsonOps.INSTANCE, metadata.get(name)).getOrThrow(JsonParseException::new);
                  return Optional.of(section);
               } else {
                  return Optional.empty();
               }
            }
         };
      }
   }

   <T> Optional<T> getSection(MetadataSectionType<T> serializer);

   default <T> Optional<MetadataSectionType.WithValue<T>> getTypedSection(final MetadataSectionType<T> type) {
      return this.getSection(type).map(type::withValue);
   }

   default List<MetadataSectionType.WithValue<?>> getTypedSections(final Collection<MetadataSectionType<?>> types) {
      return types.stream().map(this::getTypedSection).flatMap(Optional::stream).collect(Collectors.toUnmodifiableList());
   }
}
