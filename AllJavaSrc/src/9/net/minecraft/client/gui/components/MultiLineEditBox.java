package net.minecraft.client.gui.components;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

public class MultiLineEditBox extends AbstractTextAreaWidget {
   private static final int CURSOR_INSERT_WIDTH = 1;
   private static final int CURSOR_COLOR = -3092272;
   private static final String CURSOR_APPEND_CHARACTER = "_";
   private static final int PLACEHOLDER_TEXT_COLOR = ARGB.color(204, -2039584);
   private static final int CURSOR_BLINK_INTERVAL_MS = 300;
   private final Font font;
   private final Component placeholder;
   private final MultilineTextField textField;
   private final int textColor;
   private final boolean textShadow;
   private final int cursorColor;
   private long focusedTime = Util.getMillis();

   private MultiLineEditBox(
      final Font font,
      final int x,
      final int y,
      final int width,
      final int height,
      final Component placeholder,
      final Component narration,
      final int textColor,
      final boolean textShadow,
      final int cursorColor,
      final boolean showBackground,
      final boolean showDecorations
   ) {
      super(x, y, width, height, narration, showBackground, showDecorations);
      this.font = font;
      this.textShadow = textShadow;
      this.textColor = textColor;
      this.cursorColor = cursorColor;
      this.placeholder = placeholder;
      this.textField = new MultilineTextField(font, width - this.totalInnerPadding());
      this.textField.setCursorListener(this::scrollToCursor);
   }

   public void setCharacterLimit(final int characterLimit) {
      this.textField.setCharacterLimit(characterLimit);
   }

   public void setLineLimit(final int lineLimit) {
      this.textField.setLineLimit(lineLimit);
   }

   public void setValueListener(final Consumer<String> valueListener) {
      this.textField.setValueListener(valueListener);
   }

   public void setValue(final String value) {
      this.setValue(value, false);
   }

   public void setValue(final String value, final boolean allowOverflowLineLimit) {
      this.textField.setValue(value, allowOverflowLineLimit);
   }

   public String getValue() {
      return this.textField.value();
   }

   @Override
   public void updateWidgetNarration(final NarrationElementOutput output) {
      output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox", this.getMessage(), this.getValue()));
   }

   @Override
   public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
      if (doubleClick) {
         this.textField.selectWordAtCursor();
      } else {
         this.textField.setSelecting(event.hasShiftDown());
         this.seekCursorScreen(event.x(), event.y());
      }
   }

   @Override
   protected void onDrag(final MouseButtonEvent event, final double dx, final double dy) {
      this.textField.setSelecting(true);
      this.seekCursorScreen(event.x(), event.y());
      this.textField.setSelecting(event.hasShiftDown());
   }

   @Override
   public boolean keyPressed(final KeyEvent event) {
      return this.textField.keyPressed(event);
   }

   @Override
   public boolean charTyped(final CharacterEvent event) {
      if (this.visible && this.isFocused() && event.isAllowedChatCharacter()) {
         this.textField.insertText(event.codepointAsString());
         return true;
      } else {
         return false;
      }
   }

   @Override
   protected void renderContents(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
      String value = this.textField.value();
      if (value.isEmpty() && !this.isFocused()) {
         graphics.drawWordWrap(
            this.font, this.placeholder, this.getInnerLeft(), this.getInnerTop(), this.width - this.totalInnerPadding(), PLACEHOLDER_TEXT_COLOR
         );
      } else {
         int cursor = this.textField.cursor();
         boolean showCursor = this.isFocused() && (Util.getMillis() - this.focusedTime) / 300L % 2L == 0L;
         boolean insertCursor = cursor < value.length();
         int cursorX = 0;
         int cursorY = 0;
         int drawTop = this.getInnerTop();
         boolean hasDrawnCursor = false;

         for (MultilineTextField.StringView lineView : this.textField.iterateLines()) {
            boolean lineWithinVisibleBounds = this.withinContentAreaTopBottom(drawTop, drawTop + 9);
            int innerLeft = this.getInnerLeft();
            if (showCursor && insertCursor && cursor >= lineView.beginIndex() && cursor <= lineView.endIndex()) {
               if (lineWithinVisibleBounds) {
                  String substring = value.substring(lineView.beginIndex(), cursor);
                  graphics.drawString(this.font, substring, innerLeft, drawTop, this.textColor, this.textShadow);
                  cursorX = innerLeft + this.font.width(substring);
                  if (!hasDrawnCursor) {
                     graphics.fill(cursorX, drawTop - 1, cursorX + 1, drawTop + 1 + 9, this.cursorColor);
                     hasDrawnCursor = true;
                  }

                  graphics.drawString(this.font, value.substring(cursor, lineView.endIndex()), cursorX, drawTop, this.textColor, this.textShadow);
               }
            } else {
               if (lineWithinVisibleBounds) {
                  String substring = value.substring(lineView.beginIndex(), lineView.endIndex());
                  graphics.drawString(this.font, substring, innerLeft, drawTop, this.textColor, this.textShadow);
                  cursorX = innerLeft + this.font.width(substring) - 1;
               }

               cursorY = drawTop;
            }

            drawTop += 9;
         }

         if (showCursor && !insertCursor && this.withinContentAreaTopBottom(cursorY, cursorY + 9)) {
            graphics.drawString(this.font, "_", cursorX + 1, cursorY, this.cursorColor, this.textShadow);
         }

         if (this.textField.hasSelection()) {
            MultilineTextField.StringView selection = this.textField.getSelected();
            int drawX = this.getInnerLeft();
            drawTop = this.getInnerTop();

            for (MultilineTextField.StringView lineView : this.textField.iterateLines()) {
               if (selection.beginIndex() > lineView.endIndex()) {
                  drawTop += 9;
               } else {
                  if (lineView.beginIndex() > selection.endIndex()) {
                     break;
                  }

                  if (this.withinContentAreaTopBottom(drawTop, drawTop + 9)) {
                     int drawBegin = this.font.width(value.substring(lineView.beginIndex(), Math.max(selection.beginIndex(), lineView.beginIndex())));
                     int drawEnd;
                     if (selection.endIndex() > lineView.endIndex()) {
                        drawEnd = this.width - this.innerPadding();
                     } else {
                        drawEnd = this.font.width(value.substring(lineView.beginIndex(), selection.endIndex()));
                     }

                     graphics.textHighlight(drawX + drawBegin, drawTop, drawX + drawEnd, drawTop + 9, true);
                  }

                  drawTop += 9;
               }
            }
         }

         if (this.isHovered()) {
            graphics.requestCursor(CursorTypes.IBEAM);
         }
      }
   }

   @Override
   protected void renderDecorations(final GuiGraphics graphics) {
      super.renderDecorations(graphics);
      if (this.textField.hasCharacterLimit()) {
         int characterLimit = this.textField.characterLimit();
         Component countText = Component.translatable("gui.multiLineEditBox.character_limit", this.textField.value().length(), characterLimit);
         graphics.drawString(this.font, countText, this.getX() + this.width - this.font.width(countText), this.getY() + this.height + 4, -6250336);
      }
   }

   @Override
   public int getInnerHeight() {
      return 9 * this.textField.getLineCount();
   }

   @Override
   protected double scrollRate() {
      return 9.0 / 2.0;
   }

   private void scrollToCursor() {
      double scrollAmount = this.scrollAmount();
      MultilineTextField.StringView firstFullyVisibleLine = this.textField.getLineView((int)(scrollAmount / 9.0));
      if (this.textField.cursor() <= firstFullyVisibleLine.beginIndex()) {
         scrollAmount = this.textField.getLineAtCursor() * 9;
      } else {
         MultilineTextField.StringView lastFullyVisibleLine = this.textField.getLineView((int)((scrollAmount + this.height) / 9.0) - 1);
         if (this.textField.cursor() > lastFullyVisibleLine.endIndex()) {
            scrollAmount = this.textField.getLineAtCursor() * 9 - this.height + 9 + this.totalInnerPadding();
         }
      }

      this.setScrollAmount(scrollAmount);
   }

   private void seekCursorScreen(final double x, final double y) {
      double mouseX = x - this.getX() - this.innerPadding();
      double mouseY = y - this.getY() - this.innerPadding() + this.scrollAmount();
      this.textField.seekCursorToPoint(mouseX, mouseY);
   }

   @Override
   public void setFocused(final boolean focused) {
      super.setFocused(focused);
      if (focused) {
         this.focusedTime = Util.getMillis();
      }
   }

   public static MultiLineEditBox.Builder builder() {
      return new MultiLineEditBox.Builder();
   }

   public static class Builder {
      private int x;
      private int y;
      private Component placeholder = CommonComponents.EMPTY;
      private int textColor = -2039584;
      private boolean textShadow = true;
      private int cursorColor = -3092272;
      private boolean showBackground = true;
      private boolean showDecorations = true;

      public MultiLineEditBox.Builder setX(final int x) {
         this.x = x;
         return this;
      }

      public MultiLineEditBox.Builder setY(final int y) {
         this.y = y;
         return this;
      }

      public MultiLineEditBox.Builder setPlaceholder(final Component placeholder) {
         this.placeholder = placeholder;
         return this;
      }

      public MultiLineEditBox.Builder setTextColor(final int textColor) {
         this.textColor = textColor;
         return this;
      }

      public MultiLineEditBox.Builder setTextShadow(final boolean textShadow) {
         this.textShadow = textShadow;
         return this;
      }

      public MultiLineEditBox.Builder setCursorColor(final int cursorColor) {
         this.cursorColor = cursorColor;
         return this;
      }

      public MultiLineEditBox.Builder setShowBackground(final boolean showBackground) {
         this.showBackground = showBackground;
         return this;
      }

      public MultiLineEditBox.Builder setShowDecorations(final boolean showDecorations) {
         this.showDecorations = showDecorations;
         return this;
      }

      public MultiLineEditBox build(final Font font, final int width, final int height, final Component narration) {
         return new MultiLineEditBox(
            font,
            this.x,
            this.y,
            width,
            height,
            this.placeholder,
            narration,
            this.textColor,
            this.textShadow,
            this.cursorColor,
            this.showBackground,
            this.showDecorations
         );
      }
   }
}
