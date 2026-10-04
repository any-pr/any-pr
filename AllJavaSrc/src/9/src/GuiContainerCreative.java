package net.minecraft.src;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class GuiContainerCreative extends GuiContainer {
	private static InventoryBasic field_35311_f = new InventoryBasic("tmp", 72);
	private float field_35312_g = 0.0F;
	private boolean field_35313_h = false;
	private boolean field_35314_i;

	public GuiContainerCreative(EntityPlayer var1) {
		super(new ContainerCreative(var1));
		var1.craftingInventory = this.inventorySlots;
		this.allowUserInput = true;
		var1.addStat(AchievementList.openInventory, 1);
		this.ySize = 208;
	}

	public void updateScreen() {
		if(!this.mc.playerController.func_35640_h()) {
			this.mc.displayGuiScreen(new GuiInventory(this.mc.thePlayer));
		}

	}

	protected void func_35309_a(Slot var1, int var2, int var3, boolean var4) {
		InventoryPlayer var5;
		ItemStack var6;
		if(var1 != null) {
			if(var1.inventory == field_35311_f) {
				var5 = this.mc.thePlayer.inventory;
				var6 = var5.getItemStack();
				ItemStack var7 = var1.getStack();
				if(var6 != null && var7 != null && var6.itemID == var7.itemID) {
					if(var3 == 0) {
						if(var4) {
							var6.stackSize = var6.getMaxStackSize();
						} else if(var6.stackSize < var6.getMaxStackSize()) {
							++var6.stackSize;
						}
					} else if(var6.stackSize <= 1) {
						var5.setItemStack((ItemStack)null);
					} else {
						--var6.stackSize;
					}
				} else if(var6 != null) {
					var5.setItemStack((ItemStack)null);
				} else if(var7 == null) {
					var5.setItemStack((ItemStack)null);
				} else if(var6 == null || var6.itemID != var7.itemID) {
					var5.setItemStack(ItemStack.copyItemStack(var7));
					var6 = var5.getItemStack();
					if(var4) {
						var6.stackSize = var6.getMaxStackSize();
					}
				}
			} else {
				this.inventorySlots.slotClick(var1.slotNumber, var3, var4, this.mc.thePlayer);
				ItemStack var8 = this.inventorySlots.getSlot(var1.slotNumber).getStack();
				this.mc.playerController.func_35637_a(var8, var1.slotNumber - this.inventorySlots.inventorySlots.size() + 9 + 36);
			}
		} else {
			var5 = this.mc.thePlayer.inventory;
			if(var5.getItemStack() != null) {
				if(var3 == 0) {
					this.mc.thePlayer.dropPlayerItem(var5.getItemStack());
					this.mc.playerController.func_35639_a(var5.getItemStack());
					var5.setItemStack((ItemStack)null);
				}

				if(var3 == 1) {
					var6 = var5.getItemStack().splitStack(1);
					this.mc.thePlayer.dropPlayerItem(var6);
					this.mc.playerController.func_35639_a(var6);
					if(var5.getItemStack().stackSize == 0) {
						var5.setItemStack((ItemStack)null);
					}
				}
			}
		}

	}

	public void initGui() {
		if(!this.mc.playerController.func_35640_h()) {
			this.mc.displayGuiScreen(new GuiInventory(this.mc.thePlayer));
		}

		this.controlList.clear();
	}

	protected void drawGuiContainerForegroundLayer() {
		this.fontRenderer.drawString("Item selection", 8, 6, 4210752);
	}

	public void handleMouseInput() {
		super.handleMouseInput();
		int var1 = Mouse.getEventDWheel();
		if(var1 != 0) {
			int var2 = ((ContainerCreative)this.inventorySlots).field_35375_a.size() / 8 - 8 + 1;
			if(var1 > 0) {
				var1 = 1;
			}

			if(var1 < 0) {
				var1 = -1;
			}

			this.field_35312_g = (float)((double)this.field_35312_g - (double)var1 / (double)var2);
			if(this.field_35312_g < 0.0F) {
				this.field_35312_g = 0.0F;
			}

			if(this.field_35312_g > 1.0F) {
				this.field_35312_g = 1.0F;
			}

			((ContainerCreative)this.inventorySlots).func_35374_a(this.field_35312_g);
		}

	}

	public void drawScreen(int var1, int var2, float var3) {
		boolean var4 = Mouse.isButtonDown(0);
		int var5 = (this.width - this.xSize) / 2;
		int var6 = (this.height - this.ySize) / 2;
		int var7 = var5 + 155;
		int var8 = var6 + 17;
		int var9 = var7 + 14;
		int var10 = var8 + 160 + 2;
		if(!this.field_35314_i && var4 && var1 >= var7 && var2 >= var8 && var1 < var9 && var2 < var10) {
			this.field_35313_h = true;
		}

		if(!var4) {
			this.field_35313_h = false;
		}

		this.field_35314_i = var4;
		if(this.field_35313_h) {
			this.field_35312_g = (float)(var2 - (var8 + 8)) / ((float)(var10 - var8) - 16.0F);
			if(this.field_35312_g < 0.0F) {
				this.field_35312_g = 0.0F;
			}

			if(this.field_35312_g > 1.0F) {
				this.field_35312_g = 1.0F;
			}

			((ContainerCreative)this.inventorySlots).func_35374_a(this.field_35312_g);
		}

		super.drawScreen(var1, var2, var3);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glDisable(GL11.GL_LIGHTING);
		int var11 = this.mc.renderEngine.getTexture("/gui/allitems.png");
		this.mc.renderEngine.bindTexture(var11);
		this.drawTexturedModalRect(var5 + 154, var6 + 17 + (int)((float)(var10 - var8 - 17) * this.field_35312_g), 0, 208, 16, 16);
	}

	protected void drawGuiContainerBackgroundLayer(float var1) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		int var2 = this.mc.renderEngine.getTexture("/gui/allitems.png");
		this.mc.renderEngine.bindTexture(var2);
		int var3 = (this.width - this.xSize) / 2;
		int var4 = (this.height - this.ySize) / 2;
		this.drawTexturedModalRect(var3, var4, 0, 0, this.xSize, this.ySize);
	}

	protected void actionPerformed(GuiButton var1) {
		if(var1.id == 0) {
			this.mc.displayGuiScreen(new GuiAchievements(this.mc.statFileWriter));
		}

		if(var1.id == 1) {
			this.mc.displayGuiScreen(new GuiStats(this, this.mc.statFileWriter));
		}

	}

	static InventoryBasic func_35310_g() {
		return field_35311_f;
	}
}
