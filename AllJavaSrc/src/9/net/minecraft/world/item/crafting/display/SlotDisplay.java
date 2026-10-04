package net.minecraft.world.item.crafting.display;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.block.entity.FuelValues;

public interface SlotDisplay {
   Codec<SlotDisplay> CODEC = BuiltInRegistries.SLOT_DISPLAY.byNameCodec().dispatch(SlotDisplay::type, SlotDisplay.Type::codec);
   StreamCodec<RegistryFriendlyByteBuf, SlotDisplay> STREAM_CODEC = ByteBufCodecs.registry(Registries.SLOT_DISPLAY)
      .dispatch(SlotDisplay::type, SlotDisplay.Type::streamCodec);

   <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> builder);

   SlotDisplay.Type<? extends SlotDisplay> type();

   default boolean isEnabled(final FeatureFlagSet enabledFeatures) {
      return true;
   }

   default List<ItemStack> resolveForStacks(final ContextMap context) {
      return this.resolve(context, SlotDisplay.ItemStackContentsFactory.INSTANCE).toList();
   }

   default ItemStack resolveForFirstStack(final ContextMap context) {
      return this.resolve(context, SlotDisplay.ItemStackContentsFactory.INSTANCE).findFirst().orElse(ItemStack.EMPTY);
   }

   class AnyFuel implements SlotDisplay {
      public static final SlotDisplay.AnyFuel INSTANCE = new SlotDisplay.AnyFuel();
      public static final MapCodec<SlotDisplay.AnyFuel> MAP_CODEC = MapCodec.unit(INSTANCE);
      public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.AnyFuel> STREAM_CODEC = StreamCodec.unit(INSTANCE);
      public static final SlotDisplay.Type<SlotDisplay.AnyFuel> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

      private AnyFuel() {
      }

      @Override
      public SlotDisplay.Type<SlotDisplay.AnyFuel> type() {
         return TYPE;
      }

      @Override
      public String toString() {
         return "<any fuel>";
      }

      @Override
      public <T> Stream<T> resolve(final ContextMap context, final DisplayContentsFactory<T> factory) {
         if (factory instanceof DisplayContentsFactory.ForStacks<T> stacks) {
            FuelValues fuelValues = context.getOptional(SlotDisplayContext.FUEL_VALUES);
            if (fuelValues != null) {
               return fuelValues.fuelItems().stream().map(stacks::forStack);
            }
         }

         return Stream.empty();
      }
   }

   record Composite(List<SlotDisplay> contents) implements SlotDisplay {
      public static final MapCodec<SlotDisplay.Composite> MAP_CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(SlotDisplay.CODEC.listOf().fieldOf("contents").forGetter(SlotDisplay.Composite::contents)).apply(i, SlotDisplay.Composite::new)
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.Composite> STREAM_CODEC = StreamCodec.composite(
         SlotDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()), SlotDisplay.Composite::contents, SlotDisplay.Composite::new
      );
      public static final SlotDisplay.Type<SlotDisplay.Composite> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

      @Override
      public SlotDisplay.Type<SlotDisplay.Composite> type() {
         return TYPE;
      }

      @Override
      public <T> Stream<T> resolve(final ContextMap context, final DisplayContentsFactory<T> factory) {
         return this.contents.stream().flatMap(d -> d.resolve(context, factory));
      }

      @Override
      public boolean isEnabled(final FeatureFlagSet enabledFeatures) {
         return this.contents.stream().allMatch(c -> c.isEnabled(enabledFeatures));
      }
   }

   class Empty implements SlotDisplay {
      public static final SlotDisplay.Empty INSTANCE = new SlotDisplay.Empty();
      public static final MapCodec<SlotDisplay.Empty> MAP_CODEC = MapCodec.unit(INSTANCE);
      public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.Empty> STREAM_CODEC = StreamCodec.unit(INSTANCE);
      public static final SlotDisplay.Type<SlotDisplay.Empty> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

      private Empty() {
      }

      @Override
      public SlotDisplay.Type<SlotDisplay.Empty> type() {
         return TYPE;
      }

      @Override
      public String toString() {
         return "<empty>";
      }

      @Override
      public <T> Stream<T> resolve(final ContextMap context, final DisplayContentsFactory<T> factory) {
         return Stream.empty();
      }
   }

   record ItemSlotDisplay(Holder<Item> item) implements SlotDisplay {
      public static final MapCodec<SlotDisplay.ItemSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(Item.CODEC.fieldOf("item").forGetter(SlotDisplay.ItemSlotDisplay::item)).apply(i, SlotDisplay.ItemSlotDisplay::new)
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.ItemSlotDisplay> STREAM_CODEC = StreamCodec.composite(
         Item.STREAM_CODEC, SlotDisplay.ItemSlotDisplay::item, SlotDisplay.ItemSlotDisplay::new
      );
      public static final SlotDisplay.Type<SlotDisplay.ItemSlotDisplay> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

      public ItemSlotDisplay(final Item item) {
         this(item.builtInRegistryHolder());
      }

      @Override
      public SlotDisplay.Type<SlotDisplay.ItemSlotDisplay> type() {
         return TYPE;
      }

      @Override
      public <T> Stream<T> resolve(final ContextMap context, final DisplayContentsFactory<T> factory) {
         return factory instanceof DisplayContentsFactory.ForStacks<T> stacks ? Stream.of(stacks.forStack(this.item)) : Stream.empty();
      }

      @Override
      public boolean isEnabled(final FeatureFlagSet enabledFeatures) {
         return this.item.value().isEnabled(enabledFeatures);
      }
   }

   class ItemStackContentsFactory implements DisplayContentsFactory.ForStacks<ItemStack> {
      public static final SlotDisplay.ItemStackContentsFactory INSTANCE = new SlotDisplay.ItemStackContentsFactory();

      public ItemStack forStack(final ItemStack stack) {
         return stack;
      }
   }

   record ItemStackSlotDisplay(ItemStack stack) implements SlotDisplay {
      public static final MapCodec<SlotDisplay.ItemStackSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(ItemStack.STRICT_CODEC.fieldOf("item").forGetter(SlotDisplay.ItemStackSlotDisplay::stack))
            .apply(i, SlotDisplay.ItemStackSlotDisplay::new)
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.ItemStackSlotDisplay> STREAM_CODEC = StreamCodec.composite(
         ItemStack.STREAM_CODEC, SlotDisplay.ItemStackSlotDisplay::stack, SlotDisplay.ItemStackSlotDisplay::new
      );
      public static final SlotDisplay.Type<SlotDisplay.ItemStackSlotDisplay> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

      @Override
      public SlotDisplay.Type<SlotDisplay.ItemStackSlotDisplay> type() {
         return TYPE;
      }

      @Override
      public <T> Stream<T> resolve(final ContextMap context, final DisplayContentsFactory<T> factory) {
         return factory instanceof DisplayContentsFactory.ForStacks<T> stacks ? Stream.of(stacks.forStack(this.stack)) : Stream.empty();
      }

      @Override
      public boolean equals(final Object o) {
         return this == o || o instanceof SlotDisplay.ItemStackSlotDisplay that && ItemStack.matches(this.stack, that.stack);
      }

      @Override
      public boolean isEnabled(final FeatureFlagSet enabledFeatures) {
         return this.stack.getItem().isEnabled(enabledFeatures);
      }
   }

   record SmithingTrimDemoSlotDisplay(SlotDisplay base, SlotDisplay material, Holder<TrimPattern> pattern) implements SlotDisplay {
      public static final MapCodec<SlotDisplay.SmithingTrimDemoSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(
               SlotDisplay.CODEC.fieldOf("base").forGetter(SlotDisplay.SmithingTrimDemoSlotDisplay::base),
               SlotDisplay.CODEC.fieldOf("material").forGetter(SlotDisplay.SmithingTrimDemoSlotDisplay::material),
               TrimPattern.CODEC.fieldOf("pattern").forGetter(SlotDisplay.SmithingTrimDemoSlotDisplay::pattern)
            )
            .apply(i, SlotDisplay.SmithingTrimDemoSlotDisplay::new)
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.SmithingTrimDemoSlotDisplay> STREAM_CODEC = StreamCodec.composite(
         SlotDisplay.STREAM_CODEC,
         SlotDisplay.SmithingTrimDemoSlotDisplay::base,
         SlotDisplay.STREAM_CODEC,
         SlotDisplay.SmithingTrimDemoSlotDisplay::material,
         TrimPattern.STREAM_CODEC,
         SlotDisplay.SmithingTrimDemoSlotDisplay::pattern,
         SlotDisplay.SmithingTrimDemoSlotDisplay::new
      );
      public static final SlotDisplay.Type<SlotDisplay.SmithingTrimDemoSlotDisplay> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

      @Override
      public SlotDisplay.Type<SlotDisplay.SmithingTrimDemoSlotDisplay> type() {
         return TYPE;
      }

      @Override
      public <T> Stream<T> resolve(final ContextMap context, final DisplayContentsFactory<T> factory) {
         if (factory instanceof DisplayContentsFactory.ForStacks<T> stacks) {
            HolderLookup.Provider registries = context.getOptional(SlotDisplayContext.REGISTRIES);
            if (registries != null) {
               RandomSource randomSource = RandomSource.create(System.identityHashCode(this));
               List<ItemStack> bases = this.base.resolveForStacks(context);
               if (bases.isEmpty()) {
                  return Stream.empty();
               }

               List<ItemStack> materials = this.material.resolveForStacks(context);
               if (materials.isEmpty()) {
                  return Stream.empty();
               }

               return Stream.<ItemStack>generate(() -> {
                  ItemStack base = Util.getRandom(bases, randomSource);
                  ItemStack material = Util.getRandom(materials, randomSource);
                  return SmithingTrimRecipe.applyTrim(registries, base, material, this.pattern);
               }).limit(256L).filter(s -> !s.isEmpty()).limit(16L).map(stacks::forStack);
            }
         }

         return Stream.empty();
      }
   }

   record TagSlotDisplay(TagKey<Item> tag) implements SlotDisplay {
      public static final MapCodec<SlotDisplay.TagSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(SlotDisplay.TagSlotDisplay::tag)).apply(i, SlotDisplay.TagSlotDisplay::new)
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.TagSlotDisplay> STREAM_CODEC = StreamCodec.composite(
         TagKey.streamCodec(Registries.ITEM), SlotDisplay.TagSlotDisplay::tag, SlotDisplay.TagSlotDisplay::new
      );
      public static final SlotDisplay.Type<SlotDisplay.TagSlotDisplay> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

      @Override
      public SlotDisplay.Type<SlotDisplay.TagSlotDisplay> type() {
         return TYPE;
      }

      @Override
      public <T> Stream<T> resolve(final ContextMap context, final DisplayContentsFactory<T> factory) {
         if (factory instanceof DisplayContentsFactory.ForStacks<T> stacks) {
            HolderLookup.Provider registries = context.getOptional(SlotDisplayContext.REGISTRIES);
            if (registries != null) {
               return registries.lookupOrThrow(Registries.ITEM).get(this.tag).map(t -> t.stream().map(stacks::forStack)).stream().flatMap(s -> s);
            }
         }

         return Stream.empty();
      }
   }

   record Type<T extends SlotDisplay>(MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {
   }

   record WithRemainder(SlotDisplay input, SlotDisplay remainder) implements SlotDisplay {
      public static final MapCodec<SlotDisplay.WithRemainder> MAP_CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(
               SlotDisplay.CODEC.fieldOf("input").forGetter(SlotDisplay.WithRemainder::input),
               SlotDisplay.CODEC.fieldOf("remainder").forGetter(SlotDisplay.WithRemainder::remainder)
            )
            .apply(i, SlotDisplay.WithRemainder::new)
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.WithRemainder> STREAM_CODEC = StreamCodec.composite(
         SlotDisplay.STREAM_CODEC,
         SlotDisplay.WithRemainder::input,
         SlotDisplay.STREAM_CODEC,
         SlotDisplay.WithRemainder::remainder,
         SlotDisplay.WithRemainder::new
      );
      public static final SlotDisplay.Type<SlotDisplay.WithRemainder> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

      @Override
      public SlotDisplay.Type<SlotDisplay.WithRemainder> type() {
         return TYPE;
      }

      @Override
      public <T> Stream<T> resolve(final ContextMap context, final DisplayContentsFactory<T> factory) {
         if (factory instanceof DisplayContentsFactory.ForRemainders<T> remainders) {
            List<T> resolvedRemainders = this.remainder.resolve(context, factory).toList();
            return this.input.resolve(context, factory).map(input -> remainders.addRemainder((T)input, resolvedRemainders));
         } else {
            return this.input.resolve(context, factory);
         }
      }

      @Override
      public boolean isEnabled(final FeatureFlagSet enabledFeatures) {
         return this.input.isEnabled(enabledFeatures) && this.remainder.isEnabled(enabledFeatures);
      }
   }
}
