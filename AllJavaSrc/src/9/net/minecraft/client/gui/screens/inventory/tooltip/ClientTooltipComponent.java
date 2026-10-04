package net.minecraft.client.gui.screens.inventory.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public interface ClientTooltipComponent {
   static ClientTooltipComponent create(final FormattedCharSequence charSequence) {
      return new ClientTextTooltip(charSequence);
   }

   static ClientTooltipComponent create(final TooltipComponent component) {
      return switch (component) {
         case BundleTooltip bundleTooltip -> new ClientBundleTooltip(bundleTooltip.contents());
         case ClientActivePlayersTooltip.ActivePlayersTooltip activePlayersTooltip -> new ClientActivePlayersTooltip(activePlayersTooltip);
         default -> throw new IllegalArgumentException("Unknown TooltipComponent");
      };
   }

   int getHeight(final Font font);

   int getWidth(final Font font);

   default boolean showTooltipWithItemInHand() {
      return false;
   }

   default void renderText(final GuiGraphics guiGraphics, final Font font, final int x, final int y) {
   }

   default void renderImage(final Font font, final int x, final int y, final int w, final int h, final GuiGraphics graphics) {
   }
}
