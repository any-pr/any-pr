package net.minecraft.src;

public class ContainerChest extends Container {
	private IInventory lowerChestInventory;
	private int numRows;

	public ContainerChest(IInventory var1, IInventory var2) {
		this.lowerChestInventory = var2;
		this.numRows = var2.getSizeInventory() / 9;
		var2.func_35161_e();
		int var3 = (this.numRows - 4) * 18;

		int var4;
		int var5;
		for(var4 = 0; var4 < this.numRows; ++var4) {
			for(var5 = 0; var5 < 9; ++var5) {
				this.addSlot(new Slot(var2, var5 + var4 * 9, 8 + var5 * 18, 18 + var4 * 18));
			}
		}

		for(var4 = 0; var4 < 3; ++var4) {
			for(var5 = 0; var5 < 9; ++var5) {
				this.addSlot(new Slot(var1, var5 + var4 * 9 + 9, 8 + var5 * 18, 103 + var4 * 18 + var3));
			}
		}

		for(var4 = 0; var4 < 9; ++var4) {
			this.addSlot(new Slot(var1, var4, 8 + var4 * 18, 161 + var3));
		}

	}

	public boolean canInteractWith(EntityPlayer var1) {
		return this.lowerChestInventory.canInteractWith(var1);
	}

	public ItemStack getStackInSlot(int var1) {
		ItemStack var2 = null;
		Slot var3 = (Slot)this.inventorySlots.get(var1);
		if(var3 != null && var3.getHasStack()) {
			ItemStack var4 = var3.getStack();
			var2 = var4.copy();
			if(var1 < this.numRows * 9) {
				if(!this.func_28126_a(var4, this.numRows * 9, this.inventorySlots.size(), true)) {
					return null;
				}
			} else if(!this.func_28126_a(var4, 0, this.numRows * 9, false)) {
				return null;
			}

			if(var4.stackSize == 0) {
				var3.putStack((ItemStack)null);
			} else {
				var3.onSlotChanged();
			}
		}

		return var2;
	}

	public void onCraftGuiClosed(EntityPlayer var1) {
		super.onCraftGuiClosed(var1);
		this.lowerChestInventory.func_35162_t_();
	}
}
