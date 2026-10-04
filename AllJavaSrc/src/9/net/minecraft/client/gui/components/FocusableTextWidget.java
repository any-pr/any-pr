package net.minecraft.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public class FocusableTextWidget extends MultiLineTextWidget {
   public static final int DEFAULT_PADDING = 4;
   private final int padding;
   private final int maxWidth;
   private final boolean alwaysShowBorder;
   private final FocusableTextWidget.BackgroundFill backgroundFill;

   private FocusableTextWidget(
      final Component message,
      final Font font,
      final int padding,
      final int maxWidth,
      final FocusableTextWidget.BackgroundFill backgroundFill,
      final boolean alwaysShowBorder
   ) {
      super(message, font);
      this.active = true;
      this.padding = padding;
      this.maxWidth = maxWidth;
      this.alwaysShowBorder = alwaysShowBorder;
      this.backgroundFill = backgroundFill;
      this.updateWidth();
      this.updateHeight();
      this.setCentered(true);
   }

   @Override
   protected void updateWidgetNarration(final NarrationElementOutput output) {
      output.add(NarratedElementType.TITLE, this.getMessage());
   }

   @Override
   public void renderWidget(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
      int borderColor = this.alwaysShowBorder && !this.isFocused() ? ARGB.color(this.alpha, -6250336) : ARGB.white(this.alpha);
      switch (this.backgroundFill) {
         case ALWAYS:
            graphics.fill(this.getX() + 1, this.getY(), this.getRight(), this.getBottom(), ARGB.black(this.alpha));
            break;
         case ON_FOCUS:
            if (this.isFocused()) {
               graphics.fill(this.getX() + 1, this.getY(), this.getRight(), this.getBottom(), ARGB.black(this.alpha));
            }
         case NEVER:
      }

      if (this.isFocused() || this.alwaysShowBorder) {
         graphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), borderColor);
      }

      super.renderWidget(graphics, mouseX, mouseY, a);
   }

   @Override
   protected int getTextX() {
      return this.getX() + this.padding;
   }

   @Override
   protected int getTextY() {
      return super.getTextY() + this.padding;
   }

   @Override
   public MultiLineTextWidget setMaxWidth(final int maxWidth) {
      return super.setMaxWidth(maxWidth - this.padding * 2);
   }

   @Override
   public int getWidth() {
      return this.width;
   }

   @Override
   public int getHeight() {
      return this.height;
   }

   public int getPadding() {
      return this.padding;
   }

   public void updateWidth() {
      if (this.maxWidth != -1) {
         this.setWidth(this.maxWidth);
         this.setMaxWidth(this.maxWidth);
      } else {
         this.setWidth(this.getFont().width(this.getMessage()) + this.padding * 2);
      }
   }

   public void updateHeight() {
      int textHeight = 9 * this.getFont().split(this.getMessage(), super.getWidth()).size();
      this.setHeight(textHeight + this.padding * 2);
   }

   @Override
   public void setMessage(final Component message) {
      this.message = message;
      int width;
      if (this.maxWidth != -1) {
         width = this.maxWidth;
      } else {
         width = this.getFont().width(message) + this.padding * 2;
      }

      this.setWidth(width);
      this.updateHeight();
   }

   @Override
   public void playDownSound(final SoundManager soundManager) {
   }

   public static FocusableTextWidget.Builder builder(final Component message, final Font font) {
      return new FocusableTextWidget.Builder(message, font);
   }

   public static FocusableTextWidget.Builder builder(final Component message, final Font font, final int padding) {
      return new FocusableTextWidget.Builder(message, font, padding);
   }

   public enum BackgroundFill {
      ALWAYS,
      ON_FOCUS,
      NEVER;
   }

   public static class Builder {
      private final Component message;
      private final Font font;
      private final int padding;
      private int maxWidth = -1;
      private boolean alwaysShowBorder = true;
      private FocusableTextWidget.BackgroundFill backgroundFill = FocusableTextWidget.BackgroundFill.ALWAYS;

      private Builder(final Component message, final Font font) {
         this(message, font, 4);
      }

      private Builder(final Component message, final Font font, final int padding) {
         this.message = message;
         this.font = font;
         this.padding = padding;
      }

      public FocusableTextWidget.Builder maxWidth(final int maxWidth) {
         this.maxWidth = maxWidth;
         return this;
      }

      public FocusableTextWidget.Builder textWidth(final int textWidth) {
         this.maxWidth = textWidth + this.padding * 2;
         return this;
      }

      public FocusableTextWidget.Builder alwaysShowBorder(final boolean alwaysShowBorder) {
         this.alwaysShowBorder = alwaysShowBorder;
         return this;
      }

      public FocusableTextWidget.Builder backgroundFill(final FocusableTextWidget.BackgroundFill backgroundFill) {
         this.backgroundFill = backgroundFill;
         return this;
      }

      public FocusableTextWidget build() {
         return new FocusableTextWidget(this.message, this.font, this.padding, this.maxWidth, this.backgroundFill, this.alwaysShowBorder);
      }
   }
}
