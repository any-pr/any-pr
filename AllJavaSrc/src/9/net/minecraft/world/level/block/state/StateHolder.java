package net.minecraft.world.level.block.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public abstract class StateHolder<O, S> {
   public static final String NAME_TAG = "Name";
   public static final String PROPERTIES_TAG = "Properties";
   private static final Function<Entry<Property<?>, Comparable<?>>, String> PROPERTY_ENTRY_TO_STRING_FUNCTION = new Function<Entry<Property<?>, Comparable<?>>, String>() {
      public String apply(final @Nullable Entry<Property<?>, Comparable<?>> entry) {
         if (entry == null) {
            return "<NULL>";
         }

         Property<?> property = entry.getKey();
         return property.getName() + "=" + this.getName(property, entry.getValue());
      }

      private <T extends Comparable<T>> String getName(final Property<T> property, final Comparable<?> value) {
         return property.getName((T)value);
      }
   };
   protected final O owner;
   private final Reference2ObjectArrayMap<Property<?>, Comparable<?>> values;
   private Map<Property<?>, S[]> neighbours;
   protected final MapCodec<S> propertiesCodec;

   protected StateHolder(final O owner, final Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, final MapCodec<S> propertiesCodec) {
      this.owner = owner;
      this.values = values;
      this.propertiesCodec = propertiesCodec;
   }

   public <T extends Comparable<T>> S cycle(final Property<T> property) {
      return this.setValue(property, findNextInCollection(property.getPossibleValues(), this.getValue(property)));
   }

   protected static <T> T findNextInCollection(final List<T> values, final T current) {
      int nextIndex = values.indexOf(current) + 1;
      return nextIndex == values.size() ? values.getFirst() : values.get(nextIndex);
   }

   @Override
   public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append(this.owner);
      if (!this.getValues().isEmpty()) {
         builder.append('[');
         builder.append(this.getValues().entrySet().stream().map(PROPERTY_ENTRY_TO_STRING_FUNCTION).collect(Collectors.joining(",")));
         builder.append(']');
      }

      return builder.toString();
   }

   @Override
   public final boolean equals(final Object obj) {
      return super.equals(obj);
   }

   @Override
   public int hashCode() {
      return super.hashCode();
   }

   public Collection<Property<?>> getProperties() {
      return Collections.unmodifiableCollection(this.values.keySet());
   }

   public boolean hasProperty(final Property<?> property) {
      return this.values.containsKey(property);
   }

   public <T extends Comparable<T>> T getValue(final Property<T> property) {
      Comparable<?> value = (Comparable<?>)this.values.get(property);
      if (value == null) {
         throw new IllegalArgumentException("Cannot get property " + property + " as it does not exist in " + this.owner);
      } else {
         return property.getValueClass().cast(value);
      }
   }

   public <T extends Comparable<T>> Optional<T> getOptionalValue(final Property<T> property) {
      return Optional.ofNullable(this.getNullableValue(property));
   }

   public <T extends Comparable<T>> T getValueOrElse(final Property<T> property, final T defaultValue) {
      return Objects.requireNonNullElse(this.getNullableValue(property), defaultValue);
   }

   private <T extends Comparable<T>> @Nullable T getNullableValue(final Property<T> property) {
      Comparable<?> value = (Comparable<?>)this.values.get(property);
      return value == null ? null : property.getValueClass().cast(value);
   }

   public <T extends Comparable<T>, V extends T> S setValue(final Property<T> property, final V value) {
      Comparable<?> oldValue = (Comparable<?>)this.values.get(property);
      if (oldValue == null) {
         throw new IllegalArgumentException("Cannot set property " + property + " as it does not exist in " + this.owner);
      } else {
         return this.setValueInternal(property, value, oldValue);
      }
   }

   public <T extends Comparable<T>, V extends T> S trySetValue(final Property<T> property, final V value) {
      Comparable<?> oldValue = (Comparable<?>)this.values.get(property);
      return (S)(oldValue == null ? this : this.setValueInternal(property, value, oldValue));
   }

   private <T extends Comparable<T>, V extends T> S setValueInternal(final Property<T> property, final V value, final Comparable<?> oldValue) {
      if (oldValue.equals(value)) {
         return (S)this;
      } else {
         int internalIndex = property.getInternalIndex((T)value);
         if (internalIndex < 0) {
            throw new IllegalArgumentException("Cannot set property " + property + " to " + value + " on " + this.owner + ", it is not an allowed value");
         } else {
            return (S)this.neighbours.get(property)[internalIndex];
         }
      }
   }

   public void populateNeighbours(final Map<Map<Property<?>, Comparable<?>>, S> statesByValues) {
      if (this.neighbours != null) {
         throw new IllegalStateException();
      }

      Map<Property<?>, S[]> neighbours = new Reference2ObjectArrayMap(this.values.size());
      ObjectIterator var3 = this.values.entrySet().iterator();

      while (var3.hasNext()) {
         Entry<Property<?>, Comparable<?>> entry = (Entry<Property<?>, Comparable<?>>)var3.next();
         Property<?> property = entry.getKey();
         neighbours.put(property, property.getPossibleValues().stream().map(value -> statesByValues.get(this.makeNeighbourValues(property, value))).toArray());
      }

      this.neighbours = neighbours;
   }

   private Map<Property<?>, Comparable<?>> makeNeighbourValues(final Property<?> property, final Comparable<?> value) {
      Map<Property<?>, Comparable<?>> neighbour = new Reference2ObjectArrayMap(this.values);
      neighbour.put(property, value);
      return neighbour;
   }

   public Map<Property<?>, Comparable<?>> getValues() {
      return this.values;
   }

   protected static <O, S extends StateHolder<O, S>> Codec<S> codec(final Codec<O> ownerCodec, final Function<O, S> defaultState) {
      return ownerCodec.dispatch(
         "Name",
         s -> s.owner,
         o -> {
            S defaultValue = defaultState.apply((O)o);
            return defaultValue.getValues().isEmpty()
               ? MapCodec.unit(defaultValue)
               : defaultValue.propertiesCodec.codec().lenientOptionalFieldOf("Properties").xmap(oo -> oo.orElse(defaultValue), Optional::of);
         }
      );
   }
}
