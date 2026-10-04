package net.minecraft.client.gui.components.debug;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import java.util.IllegalFormatException;

public class DebugFact {
   private static final Style VALUE_STYLE = Style.EMPTY.withColor(-1);
   private final MutableComponent component = Component.empty();

   public DebugFact text(final Component component) {
      this.component.append(component);
      return this;
   }

   public DebugFact text(final String string) {
      this.component.append(string);
      return this;
   }

   public DebugFact value(final String value) {
      this.component.append(Component.literal(value).withStyle(VALUE_STYLE));
      return this;
   }

   public DebugFact value(final long value) {
      return this.value(String.valueOf(value));
   }

   public DebugFact value(final int value) {
      return this.value(String.valueOf(value));
   }

   public DebugFact formattedValue(final String format, final Object... args) {
      // 🔧 MCRe 防线：调试屏幕不应因格式不匹配崩掉游戏（IllegalFormatConversionException: f != Integer
      // 曾在 SystemSpecs 的 getGuiScale() int 实参 + "%.2f" 上崩过）—— 降级为原始值拼接
      try {
         return this.value(String.format(Locale.ROOT, format, args));
      } catch (IllegalFormatException e) {
         return this.value(java.util.Arrays.toString(args));
      }
   }

   public Component result() {
      return this.component;
   }
}
