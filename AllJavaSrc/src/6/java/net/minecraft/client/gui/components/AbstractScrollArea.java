package net.minecraft.client.gui.components;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public abstract class AbstractScrollArea extends AbstractWidget {
   public static final int SCROLLBAR_WIDTH = 6;
   private double scrollAmount;
   private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("widget/scroller");
   private static final Identifier SCROLLER_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("widget/scroller_background");
   private boolean scrolling;

   public AbstractScrollArea(final int x, final int y, final int width, final int height, final Component message) {
      super(x, y, width, height, message);
   }

   @Override
   public boolean mouseScrolled(final double mx, final double my, final double scrollX, final double scrollY) {
      if (!this.visible) {
         return false;
      }

      this.setScrollAmount(this.scrollAmount() - scrollY * this.scrollRate());
      return true;
   }

   @Override
   public boolean mouseDragged(final MouseButtonEvent event, final double dx, final double dy) {
      if (this.scrolling) {
         if (event.y() < this.getY()) {
            this.setScrollAmount(0.0);
         } else if (event.y() > this.getBottom()) {
            this.setScrollAmount(this.maxScrollAmount());
         } else {
            double max = Math.max(1, this.maxScrollAmount());
            int barHeight = this.scrollerHeight();
            double yDragScale = Math.max(1.0, max / (this.height - barHeight));
            this.setScrollAmount(this.scrollAmount() + dy * yDragScale);
         }

         return true;
      } else {
         return super.mouseDragged(event, dx, dy);
      }
   }

   @Override
   public void onRelease(final MouseButtonEvent event) {
      this.scrolling = false;
   }

   public double scrollAmount() {
      return this.scrollAmount;
   }

   public void setScrollAmount(final double scrollAmount) {
      this.scrollAmount = Mth.clamp(scrollAmount, 0.0, this.maxScrollAmount());
   }

   public boolean updateScrolling(final MouseButtonEvent event) {
      this.scrolling = this.scrollbarVisible() && this.isValidClickButton(event.buttonInfo()) && this.isOverScrollbar(event.x(), event.y());
      return this.scrolling;
   }

   protected boolean isOverScrollbar(final double x, final double y) {
      return x >= this.scrollBarX() && x <= this.scrollBarX() + 6 && y >= this.getY() && y < this.getBottom();
   }

   public void refreshScrollAmount() {
      this.setScrollAmount(this.scrollAmount);
   }

   public int maxScrollAmount() {
      return Math.max(0, this.contentHeight() - this.height);
   }

   protected boolean scrollbarVisible() {
      return this.maxScrollAmount() > 0;
   }

   protected int scrollerHeight() {
      return Mth.clamp((int)((float)(this.height * this.height) / this.contentHeight()), 32, this.height - 8);
   }

   protected int scrollBarX() {
      return this.getRight() - 6;
   }

   protected int scrollBarY() {
      return Math.max(this.getY(), (int)this.scrollAmount * (this.height - this.scrollerHeight()) / this.maxScrollAmount() + this.getY());
   }

   protected void renderScrollbar(final GuiGraphics graphics, final int mouseX, final int mouseY) {
      if (this.scrollbarVisible()) {
         int scrollbarX = this.scrollBarX();
         int scrollerHeight = this.scrollerHeight();
         int scrollerY = this.scrollBarY();
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND_SPRITE, scrollbarX, this.getY(), 6, this.getHeight());
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_SPRITE, scrollbarX, scrollerY, 6, scrollerHeight);
         if (this.isOverScrollbar(mouseX, mouseY)) {
            graphics.requestCursor(this.scrolling ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
         }
      }
   }

   protected abstract int contentHeight();

   protected abstract double scrollRate();
}
