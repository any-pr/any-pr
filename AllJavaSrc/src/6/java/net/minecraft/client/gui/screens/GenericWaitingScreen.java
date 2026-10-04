package net.minecraft.client.gui.screens;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class GenericWaitingScreen extends Screen {
   private static final int TITLE_Y = 80;
   private static final int MESSAGE_Y = 120;
   private static final int MESSAGE_MAX_WIDTH = 360;
   private final @Nullable Component messageText;
   private final Component buttonLabel;
   private final Runnable buttonCallback;
   private @Nullable MultiLineLabel message;
   private Button button;
   private int disableButtonTicks;

   public static GenericWaitingScreen createWaiting(final Component title, final Component buttonLabel, final Runnable buttonCallback) {
      return new GenericWaitingScreen(title, null, buttonLabel, buttonCallback, 0);
   }

   public static GenericWaitingScreen createCompleted(
      final Component title, final Component messageText, final Component buttonLabel, final Runnable buttonCallback
   ) {
      return new GenericWaitingScreen(title, messageText, buttonLabel, buttonCallback, 20);
   }

   protected GenericWaitingScreen(
      final Component title, final @Nullable Component messageText, final Component buttonLabel, final Runnable buttonCallback, final int disableButtonTicks
   ) {
      super(title);
      this.messageText = messageText;
      this.buttonLabel = buttonLabel;
      this.buttonCallback = buttonCallback;
      this.disableButtonTicks = disableButtonTicks;
   }

   @Override
   protected void init() {
      super.init();
      if (this.messageText != null) {
         this.message = MultiLineLabel.create(this.font, this.messageText, 360);
      }

      int buttonWidth = 150;
      int buttonHeight = 20;
      int lineCount = this.message != null ? this.message.getLineCount() : 1;
      int messageButtonSpacing = Math.max(lineCount, 5) * 9;
      int buttonY = Math.min(120 + messageButtonSpacing, this.height - 40);
      this.button = this.addRenderableWidget(Button.builder(this.buttonLabel, b -> this.onClose()).bounds((this.width - 150) / 2, buttonY, 150, 20).build());
   }

   @Override
   public void tick() {
      if (this.disableButtonTicks > 0) {
         this.disableButtonTicks--;
      }

      this.button.active = this.disableButtonTicks == 0;
   }

   @Override
   public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
      super.render(graphics, mouseX, mouseY, a);
      ActiveTextCollector textRenderer = graphics.textRenderer();
      graphics.drawCenteredString(this.font, this.title, this.width / 2, 80, -1);
      if (this.message == null) {
         String loadingDots = LoadingDotsText.get(Util.getMillis());
         graphics.drawCenteredString(this.font, loadingDots, this.width / 2, 120, -6250336);
      } else {
         this.message.visitLines(TextAlignment.CENTER, this.width / 2, 120, 9, textRenderer);
      }
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return this.message != null && this.button.active;
   }

   @Override
   public void onClose() {
      this.buttonCallback.run();
   }

   @Override
   public Component getNarrationMessage() {
      return CommonComponents.joinForNarration(this.title, this.messageText != null ? this.messageText : CommonComponents.EMPTY);
   }
}
