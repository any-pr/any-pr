package net.minecraft.client.gui.screens.inventory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.item.ItemStack;

public abstract class ItemCombinerScreen<T extends ItemCombinerMenu> extends AbstractContainerScreen<T> implements ContainerListener {
   private final Identifier menuResource;

   public ItemCombinerScreen(final T menu, final Inventory inventory, final Component title, final Identifier menuResource) {
      super(menu, inventory, title);
      this.menuResource = menuResource;
   }

   protected void subInit() {
   }

   @Override
   protected void init() {
      super.init();
      this.subInit();
      this.menu.addSlotListener(this);
   }

   @Override
   public void removed() {
      super.removed();
      this.menu.removeSlotListener(this);
   }

   @Override
   public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float a) {
      super.render(graphics, mouseX, mouseY, a);
      this.renderTooltip(graphics, mouseX, mouseY);
   }

   @Override
   protected void renderBg(final GuiGraphics graphics, final float a, final int xm, final int ym) {
      graphics.blit(RenderPipelines.GUI_TEXTURED, this.menuResource, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
      this.renderErrorIcon(graphics, this.leftPos, this.topPos);
   }

   protected abstract void renderErrorIcon(final GuiGraphics graphics, final int xo, final int yo);

   @Override
   public void dataChanged(final AbstractContainerMenu container, final int id, final int value) {
   }

   @Override
   public void slotChanged(final AbstractContainerMenu container, final int slotIndex, final ItemStack itemStack) {
   }
}
