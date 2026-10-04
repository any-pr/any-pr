package net.minecraft.server.packs;

import java.util.Map;
import net.minecraft.server.packs.metadata.MetadataSectionType;

public class BuiltInMetadata {
   private static final BuiltInMetadata EMPTY = new BuiltInMetadata(Map.of());
   private final Map<MetadataSectionType<?>, ?> values;

   private BuiltInMetadata(final Map<MetadataSectionType<?>, ?> values) {
      this.values = values;
   }

   public <T> T get(final MetadataSectionType<T> section) {
      return (T)this.values.get(section);
   }

   public static BuiltInMetadata of() {
      return EMPTY;
   }

   public static <T> BuiltInMetadata of(final MetadataSectionType<T> k, final T v) {
      return new BuiltInMetadata(Map.of(k, v));
   }

   public static <T1, T2> BuiltInMetadata of(final MetadataSectionType<T1> k1, final T1 v1, final MetadataSectionType<T2> k2, final T2 v2) {
      return new BuiltInMetadata(Map.of(k1, v1, k2, (T1)v2));
   }
}
