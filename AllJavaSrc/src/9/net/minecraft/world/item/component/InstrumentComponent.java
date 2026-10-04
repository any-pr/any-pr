package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

public record InstrumentComponent(EitherHolder<Instrument> instrument) implements TooltipProvider {
   public static final Codec<InstrumentComponent> CODEC = EitherHolder.codec(Registries.INSTRUMENT, Instrument.CODEC)
      .xmap(InstrumentComponent::new, InstrumentComponent::instrument);
   public static final StreamCodec<RegistryFriendlyByteBuf, InstrumentComponent> STREAM_CODEC = EitherHolder.streamCodec(
         Registries.INSTRUMENT, Instrument.STREAM_CODEC
      )
      .map(InstrumentComponent::new, InstrumentComponent::instrument);

   public InstrumentComponent(final Holder<Instrument> instrument) {
      this(new EitherHolder<>(instrument));
   }

   @Deprecated
   public InstrumentComponent(final ResourceKey<Instrument> instrument) {
      this(new EitherHolder<>(instrument));
   }

   @Override
   public void addToTooltip(final Item.TooltipContext context, final Consumer<Component> consumer, final TooltipFlag flag, final DataComponentGetter components) {
      HolderLookup.Provider registries = context.registries();
      if (registries != null) {
         this.unwrap(registries).ifPresent(instrument -> {
            Component description = ComponentUtils.mergeStyles(instrument.value().description(), Style.EMPTY.withColor(ChatFormatting.GRAY));
            consumer.accept(description);
         });
      }
   }

   public Optional<Holder<Instrument>> unwrap(final HolderLookup.Provider registries) {
      return this.instrument.unwrap(registries);
   }
}
