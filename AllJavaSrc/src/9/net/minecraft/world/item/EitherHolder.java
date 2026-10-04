package net.minecraft.world.item;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

public record EitherHolder<T>(Either<Holder<T>, ResourceKey<T>> contents) {
   public EitherHolder(final Holder<T> holder) {
      this(Either.left(holder));
   }

   public EitherHolder(final ResourceKey<T> key) {
      this(Either.right(key));
   }

   public static <T> Codec<EitherHolder<T>> codec(final ResourceKey<Registry<T>> registry, final Codec<Holder<T>> holderCodec) {
      return Codec.either(
            holderCodec, ResourceKey.codec(registry).comapFlatMap(key -> DataResult.error(() -> "Cannot parse as key without registry"), Function.identity())
         )
         .xmap(EitherHolder::new, EitherHolder::contents);
   }

   public static <T> StreamCodec<RegistryFriendlyByteBuf, EitherHolder<T>> streamCodec(
      final ResourceKey<Registry<T>> registry, final StreamCodec<RegistryFriendlyByteBuf, Holder<T>> streamHolderCodec
   ) {
      return StreamCodec.composite(ByteBufCodecs.either(streamHolderCodec, ResourceKey.streamCodec(registry)), EitherHolder::contents, EitherHolder::new);
   }

   public Optional<T> unwrap(final Registry<T> registry) {
      return (Optional<T>)this.contents.map(holder -> Optional.of(holder.value()), registry::getOptional);
   }

   public Optional<Holder<T>> unwrap(final HolderLookup.Provider provider) {
      return (Optional<Holder<T>>)this.contents.map(Optional::of, key -> provider.get(key).map(e -> (Holder)e));
   }

   public Optional<ResourceKey<T>> key() {
      return (Optional<ResourceKey<T>>)this.contents.map(Holder::unwrapKey, Optional::of);
   }
}
