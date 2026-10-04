package net.minecraft.client.gui.screens.inventory.tooltip;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;

public class ClientBundleTooltip implements ClientTooltipComponent {
   private static final Identifier PROGRESSBAR_BORDER_SPRITE = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_border");
   private static final Identifier PROGRESSBAR_FILL_SPRITE = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_fill");
   private static final Identifier PROGRESSBAR_FULL_SPRITE = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_full");
   private static final Identifier SLOT_HIGHLIGHT_BACK_SPRITE = Identifier.withDefaultNamespace("container/bundle/slot_highlight_back");
   private static final Identifier SLOT_HIGHLIGHT_FRONT_SPRITE = Identifier.withDefaultNamespace("container/bundle/slot_highlight_front");
   private static final Identifier SLOT_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("container/bundle/slot_background");
   private static final int SLOT_MARGIN = 4;
   private static final int SLOT_SIZE = 24;
   private static final int GRID_WIDTH = 96;
   private static final int PROGRESSBAR_HEIGHT = 13;
   private static final int PROGRESSBAR_WIDTH = 96;
   private static final int PROGRESSBAR_BORDER = 1;
   private static final int PROGRESSBAR_FILL_MAX = 94;
   private static final int PROGRESSBAR_MARGIN_Y = 4;
   private static final Component BUNDLE_FULL_TEXT = Component.translatable("item.minecraft.bundle.full");
   private static final Component BUNDLE_EMPTY_TEXT = Component.translatable("item.minecraft.bundle.empty");
   private static final Component BUNDLE_EMPTY_DESCRIPTION = Component.translatable("item.minecraft.bundle.empty.description");
   private final BundleContents contents;

   public ClientBundleTooltip(final BundleContents contents) {
      this.contents = contents;
   }

   @Override
   public int getHeight(final Font font) {
      return this.contents.isEmpty() ? getEmptyBundleBackgroundHeight(font) : this.backgroundHeight();
   }

   @Override
   public int getWidth(final Font font) {
      return 96;
   }

   @Override
   public boolean showTooltipWithItemInHand() {
      return true;
   }

   private static int getEmptyBundleBackgroundHeight(final Font font) {
      return getEmptyBundleDescriptionTextHeight(font) + 13 + 8;
   }

   private int backgroundHeight() {
      return this.itemGridHeight() + 13 + 8;
   }

   private int itemGridHeight() {
      return this.gridSizeY() * 24;
   }

   private int getContentXOffset(final int tooltipWidth) {
      return (tooltipWidth - 96) / 2;
   }

   private int gridSizeY() {
      return Mth.positiveCeilDiv(this.slotCount(), 4);
   }

   private int slotCount() {
      return Math.min(12, this.contents.size());
   }

   @Override
   public void renderImage(final Font font, final int x, final int y, final int w, final int h, final GuiGraphics graphics) {
      if (this.contents.isEmpty()) {
         this.renderEmptyBundleTooltip(font, x, y, w, h, graphics);
      } else {
         this.renderBundleWithItemsTooltip(font, x, y, w, h, graphics);
      }
   }

   private void renderEmptyBundleTooltip(final Font font, final int x, final int y, final int w, final int h, final GuiGraphics graphics) {
      drawEmptyBundleDescriptionText(x + this.getContentXOffset(w), y, font, graphics);
      this.drawProgressbar(x + this.getContentXOffset(w), y + getEmptyBundleDescriptionTextHeight(font) + 4, font, graphics);
   }

   private void renderBundleWithItemsTooltip(final Font font, final int x, final int y, final int w, final int h, final GuiGraphics graphics) {
      boolean isOverflowing = this.contents.size() > 12;
      List<ItemStack> shownItems = this.getShownItems(this.contents.getNumberOfItemsToShow());
      int xStartPos = x + this.getContentXOffset(w) + 96;
      int yStartPos = y + this.gridSizeY() * 24;
      int slotNumber = 1;

      for (int rowNumber = 1; rowNumber <= this.gridSizeY(); rowNumber++) {
         for (int columnNumber = 1; columnNumber <= 4; columnNumber++) {
            int drawX = xStartPos - columnNumber * 24;
            int drawY = yStartPos - rowNumber * 24;
            if (shouldRenderSurplusText(isOverflowing, columnNumber, rowNumber)) {
               renderCount(drawX, drawY, this.getAmountOfHiddenItems(shownItems), font, graphics);
            } else if (shouldRenderItemSlot(shownItems, slotNumber)) {
               this.renderSlot(slotNumber, drawX, drawY, shownItems, slotNumber, font, graphics);
               slotNumber++;
            }
         }
      }

      this.drawSelectedItemTooltip(font, graphics, x, y, w);
      this.drawProgressbar(x + this.getContentXOffset(w), y + this.itemGridHeight() + 4, font, graphics);
   }

   private List<ItemStack> getShownItems(final int amountOfItemsToShow) {
      int lastToDisplay = Math.min(this.contents.size(), amountOfItemsToShow);
      return this.contents.itemCopyStream().toList().subList(0, lastToDisplay);
   }

   private static boolean shouldRenderSurplusText(final boolean isOverflowing, final int column, final int row) {
      return isOverflowing && column * row == 1;
   }

   private static boolean shouldRenderItemSlot(final List<ItemStack> shownItems, final int slotNumber) {
      return shownItems.size() >= slotNumber;
   }

   private int getAmountOfHiddenItems(final List<ItemStack> shownItems) {
      return this.contents.itemCopyStream().skip(shownItems.size()).mapToInt(ItemStack::getCount).sum();
   }

   private void renderSlot(
      final int slotNumber,
      final int drawX,
      final int drawY,
      final List<ItemStack> shownItems,
      final int slotIndex,
      final Font font,
      final GuiGraphics graphics
   ) {
      int itemVisualOrderIndex = shownItems.size() - slotNumber;
      boolean hasHighlight = itemVisualOrderIndex == this.contents.getSelectedItem();
      ItemStack item = shownItems.get(itemVisualOrderIndex);
      if (hasHighlight) {
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK_SPRITE, drawX, drawY, 24, 24);
      } else {
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND_SPRITE, drawX, drawY, 24, 24);
      }

      graphics.renderItem(item, drawX + 4, drawY + 4, slotIndex);
      graphics.renderItemDecorations(font, item, drawX + 4, drawY + 4);
      if (hasHighlight) {
         graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT_SPRITE, drawX, drawY, 24, 24);
      }
   }

   private static void renderCount(final int drawX, final int drawY, final int hiddenItemCount, final Font font, final GuiGraphics graphics) {
      graphics.drawCenteredString(font, "+" + hiddenItemCount, drawX + 12, drawY + 10, -1);
   }

   private void drawSelectedItemTooltip(final Font font, final GuiGraphics graphics, final int x, final int y, final int w) {
      if (this.contents.hasSelectedItem()) {
         ItemStack itemStack = this.contents.getItemUnsafe(this.contents.getSelectedItem());
         Component selectedItemName = itemStack.getStyledHoverName();
         int textWidth = font.width(selectedItemName.getVisualOrderText());
         int centerTooltip = x + w / 2 - 12;
         ClientTooltipComponent selectedItemNameTooltip = ClientTooltipComponent.create(selectedItemName.getVisualOrderText());
         graphics.renderTooltip(
            font,
            List.of(selectedItemNameTooltip),
            centerTooltip - textWidth / 2,
            y - 15,
            DefaultTooltipPositioner.INSTANCE,
            itemStack.get(DataComponents.TOOLTIP_STYLE)
         );
      }
   }

   private void drawProgressbar(final int x, final int y, final Font font, final GuiGraphics graphics) {
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.getProgressBarTexture(), x + 1, y, this.getProgressBarFill(), 13);
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESSBAR_BORDER_SPRITE, x, y, 96, 13);
      Component progressBarFillText = this.getProgressBarFillText();
      if (progressBarFillText != null) {
         graphics.drawCenteredString(font, progressBarFillText, x + 48, y + 3, -1);
      }
   }

   private static void drawEmptyBundleDescriptionText(final int x, final int y, final Font font, final GuiGraphics graphics) {
      graphics.drawWordWrap(font, BUNDLE_EMPTY_DESCRIPTION, x, y, 96, -5592406);
   }

   private static int getEmptyBundleDescriptionTextHeight(final Font font) {
      return font.split(BUNDLE_EMPTY_DESCRIPTION, 96).size() * 9;
   }

   private int getProgressBarFill() {
      return Mth.clamp(Mth.mulAndTruncate(this.contents.weight(), 94), 0, 94);
   }

   private Identifier getProgressBarTexture() {
      return this.contents.weight().compareTo(Fraction.ONE) >= 0 ? PROGRESSBAR_FULL_SPRITE : PROGRESSBAR_FILL_SPRITE;
   }

   private @Nullable Component getProgressBarFillText() {
      if (this.contents.isEmpty()) {
         return BUNDLE_EMPTY_TEXT;
      } else {
         return this.contents.weight().compareTo(Fraction.ONE) >= 0 ? BUNDLE_FULL_TEXT : null;
      }
   }
}
