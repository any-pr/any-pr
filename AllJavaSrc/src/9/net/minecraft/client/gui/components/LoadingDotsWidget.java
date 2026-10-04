package net.minecraft.client.gui.components;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.LoadingDotsText;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class LoadingDotsWidget extends AbstractWidget {
   private final Font font;

   public LoadingDotsWidget(final Font font, final Component message) {
      super(0, 0, font.width(message), 9 * 3, message);
      this.font = font;
   }

   @Override
   protected void renderWidget(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
      int centerX = this.getX() + this.getWidth() / 2;
      int centerY = this.getY() + this.getHeight() / 2;
      Component message = this.getMessage();
      graphics.drawString(this.font, message, centerX - this.font.width(message) / 2, centerY - 9, -1);
      String dots = LoadingDotsText.get(Util.getMillis());
      graphics.drawString(this.font, dots, centerX - this.font.width(dots) / 2, centerY + 9, -8355712);
   }

   @Override
   protected void updateWidgetNarration(final NarrationElementOutput output) {
   }

   @Override
   public void playDownSound(final SoundManager soundManager) {
   }

   @Override
   public boolean isActive() {
      return false;
   }

   @Override
   public @Nullable ComponentPath nextFocusPath(final FocusNavigationEvent navigationEvent) {
      return null;
   }
}
