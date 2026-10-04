package net.minecraft.client.gui.components.debug;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.network.chat.Component;

public class DebugGroup {
   private final Component title;
   private final OptionalInt accentColor;
   private final Optional<DebugColumn.Side> preferredColumn;

   protected DebugGroup(final Component title, final OptionalInt accentColor, final Optional<DebugColumn.Side> preferredColumn) {
      this.title = title;
      this.accentColor = accentColor;
      this.preferredColumn = preferredColumn;
   }

   public Component title() {
      return this.title;
   }

   public OptionalInt accentColor() {
      return this.accentColor;
   }

   public Optional<DebugColumn.Side> preferredColumn() {
      return this.preferredColumn;
   }

   public static class Builder {
      private final Component title;
      private OptionalInt accentColor = OptionalInt.empty();
      private Optional<DebugColumn.Side> preferredColumn = Optional.empty();

      protected Builder(final Component title) {
         this.title = title;
      }

      public static DebugGroup.Builder titleless() {
         return titled(Component.empty());
      }

      public static DebugGroup.Builder titled(final String title) {
         return titled(Component.literal(title));
      }

      public static DebugGroup.Builder titled(final Component title) {
         return new DebugGroup.Builder(title);
      }

      public DebugGroup.Builder withAccentColor(final int rgb) {
         this.accentColor = OptionalInt.of(rgb);
         return this;
      }

      public DebugGroup.Builder withPreferredColumn(final DebugColumn.Side column) {
         this.preferredColumn = Optional.of(column);
         return this;
      }

      public DebugGroup build() {
         return new DebugGroup(this.title, this.accentColor, this.preferredColumn);
      }
   }
}
