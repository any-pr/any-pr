package net.minecraft.client.gui.screens;

import net.minecraft.client.Options;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonLinks;
import net.minecraft.util.Util;

public class DemoIntroScreen extends Screen {
   private static final Identifier DEMO_BACKGROUND_LOCATION = Identifier.withDefaultNamespace("textures/gui/demo_background.png");
   private static final int BACKGROUND_TEXTURE_WIDTH = 256;
   private static final int BACKGROUND_TEXTURE_HEIGHT = 256;
   private static final int TEXT_COLOR = -14737633;
   private MultiLineLabel movementMessage = MultiLineLabel.EMPTY;
   private MultiLineLabel durationMessage = MultiLineLabel.EMPTY;

   public DemoIntroScreen() {
      super(Component.translatable("demo.help.title"));
   }

   @Override
   protected void init() {
      int yo = -16;
      this.addRenderableWidget(Button.builder(Component.translatable("demo.help.buy"), button -> {
         button.active = false;
         Util.getPlatform().openUri(CommonLinks.BUY_MINECRAFT_JAVA);
      }).bounds(this.width / 2 - 116, this.height / 2 + 62 + -16, 114, 20).build());
      this.addRenderableWidget(Button.builder(Component.translatable("demo.help.later"), button -> {
         this.minecraft.setScreen(null);
         this.minecraft.mouseHandler.grabMouse();
      }).bounds(this.width / 2 + 2, this.height / 2 + 62 + -16, 114, 20).build());
      Options options = this.minecraft.options;
      this.movementMessage = MultiLineLabel.create(
         this.font,
         this.movementMessage(
            Component.translatable(
               "demo.help.movementShort",
               options.keyUp.getTranslatedKeyMessage(),
               options.keyLeft.getTranslatedKeyMessage(),
               options.keyDown.getTranslatedKeyMessage(),
               options.keyRight.getTranslatedKeyMessage()
            )
         ),
         this.movementMessage(Component.translatable("demo.help.movementMouse")),
         this.movementMessage(Component.translatable("demo.help.jump", options.keyJump.getTranslatedKeyMessage())),
         this.movementMessage(Component.translatable("demo.help.inventory", options.keyInventory.getTranslatedKeyMessage()))
      );
      this.durationMessage = MultiLineLabel.create(this.font, Component.translatable("demo.help.fullWrapped").withoutShadow().withColor(-14737633), 218);
   }

   private Component movementMessage(final MutableComponent line) {
      return line.withoutShadow().withColor(-11579569);
   }

   @Override
   public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
      super.renderBackground(graphics, mouseX, mouseY, a);
      int xo = (this.width - 248) / 2;
      int yo = (this.height - 166) / 2;
      graphics.blit(RenderPipelines.GUI_TEXTURED, DEMO_BACKGROUND_LOCATION, xo, yo, 0.0F, 0.0F, 248, 166, 256, 256);
   }

   @Override
   public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
      super.render(graphics, mouseX, mouseY, a);
      int x = (this.width - 248) / 2 + 10;
      int y = (this.height - 166) / 2 + 8;
      ActiveTextCollector textRenderer = graphics.textRenderer();
      graphics.drawString(this.font, this.title, x, y, -14737633, false);
      y = this.movementMessage.visitLines(TextAlignment.LEFT, x, y + 12, 12, textRenderer);
      this.durationMessage.visitLines(TextAlignment.LEFT, x, y + 20, 9, textRenderer);
   }
}
