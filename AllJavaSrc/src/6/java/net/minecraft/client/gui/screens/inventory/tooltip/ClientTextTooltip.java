package net.minecraft.client.gui.screens.inventory.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

public class ClientTextTooltip implements ClientTooltipComponent {
   private final FormattedCharSequence text;

   public ClientTextTooltip(final FormattedCharSequence text) {
      this.text = text;
   }

   @Override
   public int getWidth(final Font font) {
      return font.width(this.text);
   }

   @Override
   public int getHeight(final Font font) {
      return 10;
   }

   @Override
   public void renderText(final GuiGraphics guiGraphics, final Font font, final int x, final int y) {
      guiGraphics.drawString(font, this.text, x, y, -1, true);
   }
}
