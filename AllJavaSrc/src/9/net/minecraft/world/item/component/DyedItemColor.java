package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public record DyedItemColor(int rgb) implements TooltipProvider {
   public static final Codec<DyedItemColor> CODEC = ExtraCodecs.RGB_COLOR_CODEC.xmap(DyedItemColor::new, DyedItemColor::rgb);
   public static final StreamCodec<ByteBuf, DyedItemColor> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.INT, DyedItemColor::rgb, DyedItemColor::new);
   public static final int LEATHER_COLOR = -6265536;

   public static int getOrDefault(final ItemStack itemStack, final int defaultColor) {
      DyedItemColor color = itemStack.get(DataComponents.DYED_COLOR);
      return color != null ? ARGB.opaque(color.rgb()) : defaultColor;
   }

   public static ItemStack applyDyes(final ItemStack itemStack, final List<DyeItem> dyes) {
      if (!itemStack.is(ItemTags.DYEABLE)) {
         return ItemStack.EMPTY;
      }

      ItemStack result = itemStack.copyWithCount(1);
      int redTotal = 0;
      int greenTotal = 0;
      int blueTotal = 0;
      int intensityTotal = 0;
      int colorCount = 0;
      DyedItemColor currentDye = result.get(DataComponents.DYED_COLOR);
      if (currentDye != null) {
         int red = ARGB.red(currentDye.rgb());
         int green = ARGB.green(currentDye.rgb());
         int blue = ARGB.blue(currentDye.rgb());
         intensityTotal += Math.max(red, Math.max(green, blue));
         redTotal += red;
         greenTotal += green;
         blueTotal += blue;
         colorCount++;
      }

      for (DyeItem dye : dyes) {
         int color = dye.getDyeColor().getTextureDiffuseColor();
         int red = ARGB.red(color);
         int green = ARGB.green(color);
         int blue = ARGB.blue(color);
         intensityTotal += Math.max(red, Math.max(green, blue));
         redTotal += red;
         greenTotal += green;
         blueTotal += blue;
         colorCount++;
      }

      int red = redTotal / colorCount;
      int green = greenTotal / colorCount;
      int blue = blueTotal / colorCount;
      float averageIntensity = (float)intensityTotal / colorCount;
      float resultIntensity = Math.max(red, Math.max(green, blue));
      red = (int)(red * averageIntensity / resultIntensity);
      green = (int)(green * averageIntensity / resultIntensity);
      blue = (int)(blue * averageIntensity / resultIntensity);
      int rgb = ARGB.color(0, red, green, blue);
      result.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
      return result;
   }

   @Override
   public void addToTooltip(final Item.TooltipContext context, final Consumer<Component> consumer, final TooltipFlag flag, final DataComponentGetter components) {
      if (flag.isAdvanced()) {
         consumer.accept(Component.translatable("item.color", String.format(Locale.ROOT, "#%06X", this.rgb)).withStyle(ChatFormatting.GRAY));
      } else {
         consumer.accept(Component.translatable("item.dyed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
      }
   }
}
