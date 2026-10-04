package net.minecraft.world.item.component;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class ChargedProjectiles implements TooltipProvider {
   public static final ChargedProjectiles EMPTY = new ChargedProjectiles(List.of());
   public static final Codec<ChargedProjectiles> CODEC = ItemStack.CODEC.listOf().xmap(ChargedProjectiles::new, projectiles -> projectiles.items);
   public static final StreamCodec<RegistryFriendlyByteBuf, ChargedProjectiles> STREAM_CODEC = ItemStack.STREAM_CODEC
      .apply(ByteBufCodecs.list())
      .map(ChargedProjectiles::new, projectiles -> projectiles.items);
   private final List<ItemStack> items;

   private ChargedProjectiles(final List<ItemStack> items) {
      this.items = items;
   }

   public static ChargedProjectiles of(final ItemStack itemStack) {
      return new ChargedProjectiles(List.of(itemStack.copy()));
   }

   public static ChargedProjectiles of(final List<ItemStack> items) {
      return new ChargedProjectiles(List.copyOf(Lists.transform(items, ItemStack::copy)));
   }

   public boolean contains(final Item item) {
      for (ItemStack projectile : this.items) {
         if (projectile.is(item)) {
            return true;
         }
      }

      return false;
   }

   public List<ItemStack> getItems() {
      return Lists.transform(this.items, ItemStack::copy);
   }

   public boolean isEmpty() {
      return this.items.isEmpty();
   }

   @Override
   public boolean equals(final Object obj) {
      return this == obj ? true : obj instanceof ChargedProjectiles projectiles && ItemStack.listMatches(this.items, projectiles.items);
   }

   @Override
   public int hashCode() {
      return ItemStack.hashStackList(this.items);
   }

   @Override
   public String toString() {
      return "ChargedProjectiles[items=" + this.items + "]";
   }

   @Override
   public void addToTooltip(final Item.TooltipContext context, final Consumer<Component> consumer, final TooltipFlag flag, final DataComponentGetter components) {
      ItemStack current = null;
      int count = 0;

      for (ItemStack projectile : this.items) {
         if (current == null) {
            current = projectile;
            count = 1;
         } else if (ItemStack.matches(current, projectile)) {
            count++;
         } else {
            addProjectileTooltip(context, consumer, current, count);
            current = projectile;
            count = 1;
         }
      }

      if (current != null) {
         addProjectileTooltip(context, consumer, current, count);
      }
   }

   private static void addProjectileTooltip(final Item.TooltipContext context, final Consumer<Component> consumer, final ItemStack projectile, final int count) {
      if (count == 1) {
         consumer.accept(Component.translatable("item.minecraft.crossbow.projectile.single", projectile.getDisplayName()));
      } else {
         consumer.accept(Component.translatable("item.minecraft.crossbow.projectile.multiple", count, projectile.getDisplayName()));
      }

      TooltipDisplay projectileDisplay = projectile.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
      projectile.addDetailsToTooltip(
         context, projectileDisplay, null, TooltipFlag.NORMAL, line -> consumer.accept(Component.literal("  ").append(line).withStyle(ChatFormatting.GRAY))
      );
   }
}
