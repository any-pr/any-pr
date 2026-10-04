package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record UseRemainder(ItemStack convertInto) {
   public static final Codec<UseRemainder> CODEC = ItemStack.CODEC.xmap(UseRemainder::new, UseRemainder::convertInto);
   public static final StreamCodec<RegistryFriendlyByteBuf, UseRemainder> STREAM_CODEC = StreamCodec.composite(
      ItemStack.STREAM_CODEC, UseRemainder::convertInto, UseRemainder::new
   );

   public ItemStack convertIntoRemainder(
      final ItemStack usedStack,
      final int stackCountBeforeUsing,
      final boolean hasInfiniteMaterials,
      final UseRemainder.OnExtraCreatedRemainder onExtraCreatedRemainder
   ) {
      if (hasInfiniteMaterials) {
         return usedStack;
      }

      if (usedStack.getCount() >= stackCountBeforeUsing) {
         return usedStack;
      }

      ItemStack remainderStack = this.convertInto.copy();
      if (usedStack.isEmpty()) {
         return remainderStack;
      }

      onExtraCreatedRemainder.apply(remainderStack);
      return usedStack;
   }

   @Override
   public boolean equals(final Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         UseRemainder that = (UseRemainder)o;
         return ItemStack.matches(this.convertInto, that.convertInto);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return ItemStack.hashItemAndComponents(this.convertInto);
   }

   @FunctionalInterface
   public interface OnExtraCreatedRemainder {
      void apply(final ItemStack extraCreatedRemainder);
   }
}
