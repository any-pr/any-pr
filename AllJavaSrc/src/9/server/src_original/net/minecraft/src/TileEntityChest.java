package net.minecraft.src;

public class TileEntityChest extends TileEntity implements IInventory {
	private ItemStack[] chestContents = new ItemStack[36];
	public boolean field_35175_a = false;
	public TileEntityChest field_35172_b;
	public TileEntityChest field_35173_c;
	public TileEntityChest field_35170_d;
	public TileEntityChest field_35171_e;
	public float field_35168_f;
	public float field_35169_g;
	public int field_35176_h;
	private int field_35174_q;

	public int getSizeInventory() {
		return 27;
	}

	public ItemStack getStackInSlot(int var1) {
		return this.chestContents[var1];
	}

	public ItemStack decrStackSize(int var1, int var2) {
		if(this.chestContents[var1] != null) {
			ItemStack var3;
			if(this.chestContents[var1].stackSize <= var2) {
				var3 = this.chestContents[var1];
				this.chestContents[var1] = null;
				this.onInventoryChanged();
				return var3;
			} else {
				var3 = this.chestContents[var1].splitStack(var2);
				if(this.chestContents[var1].stackSize == 0) {
					this.chestContents[var1] = null;
				}

				this.onInventoryChanged();
				return var3;
			}
		} else {
			return null;
		}
	}

	public void setInventorySlotContents(int var1, ItemStack var2) {
		this.chestContents[var1] = var2;
		if(var2 != null && var2.stackSize > this.getInventoryStackLimit()) {
			var2.stackSize = this.getInventoryStackLimit();
		}

		this.onInventoryChanged();
	}

	public String getInvName() {
		return "Chest";
	}

	public void readFromNBT(NBTTagCompound var1) {
		super.readFromNBT(var1);
		NBTTagList var2 = var1.getTagList("Items");
		this.chestContents = new ItemStack[this.getSizeInventory()];

		for(int var3 = 0; var3 < var2.tagCount(); ++var3) {
			NBTTagCompound var4 = (NBTTagCompound)var2.tagAt(var3);
			int var5 = var4.getByte("Slot") & 255;
			if(var5 >= 0 && var5 < this.chestContents.length) {
				this.chestContents[var5] = ItemStack.func_35618_a(var4);
			}
		}

	}

	public void writeToNBT(NBTTagCompound var1) {
		super.writeToNBT(var1);
		NBTTagList var2 = new NBTTagList();

		for(int var3 = 0; var3 < this.chestContents.length; ++var3) {
			if(this.chestContents[var3] != null) {
				NBTTagCompound var4 = new NBTTagCompound();
				var4.setByte("Slot", (byte)var3);
				this.chestContents[var3].writeToNBT(var4);
				var2.setTag(var4);
			}
		}

		var1.setTag("Items", var2);
	}

	public int getInventoryStackLimit() {
		return 64;
	}

	public boolean canInteractWith(EntityPlayer var1) {
		return this.worldObj.getBlockTileEntity(this.xCoord, this.yCoord, this.zCoord) != this ? false : var1.getDistanceSq((double)this.xCoord + 0.5D, (double)this.yCoord + 0.5D, (double)this.zCoord + 0.5D) <= 64.0D;
	}

	public void func_35164_g() {
		super.func_35164_g();
		this.field_35175_a = false;
	}

	public void func_35167_h() {
		if(!this.field_35175_a) {
			this.field_35175_a = true;
			this.field_35172_b = null;
			this.field_35173_c = null;
			this.field_35170_d = null;
			this.field_35171_e = null;
			if(this.worldObj.getBlockId(this.xCoord - 1, this.yCoord, this.zCoord) == Block.chest.blockID) {
				this.field_35170_d = (TileEntityChest)this.worldObj.getBlockTileEntity(this.xCoord - 1, this.yCoord, this.zCoord);
			}

			if(this.worldObj.getBlockId(this.xCoord + 1, this.yCoord, this.zCoord) == Block.chest.blockID) {
				this.field_35173_c = (TileEntityChest)this.worldObj.getBlockTileEntity(this.xCoord + 1, this.yCoord, this.zCoord);
			}

			if(this.worldObj.getBlockId(this.xCoord, this.yCoord, this.zCoord - 1) == Block.chest.blockID) {
				this.field_35172_b = (TileEntityChest)this.worldObj.getBlockTileEntity(this.xCoord, this.yCoord, this.zCoord - 1);
			}

			if(this.worldObj.getBlockId(this.xCoord, this.yCoord, this.zCoord + 1) == Block.chest.blockID) {
				this.field_35171_e = (TileEntityChest)this.worldObj.getBlockTileEntity(this.xCoord, this.yCoord, this.zCoord + 1);
			}

			if(this.field_35172_b != null) {
				this.field_35172_b.func_35164_g();
			}

			if(this.field_35171_e != null) {
				this.field_35171_e.func_35164_g();
			}

			if(this.field_35173_c != null) {
				this.field_35173_c.func_35164_g();
			}

			if(this.field_35170_d != null) {
				this.field_35170_d.func_35164_g();
			}

		}
	}

	public void updateEntity() {
		super.updateEntity();
		this.func_35167_h();
		if(++this.field_35174_q % 20 * 4 == 0) {
			this.worldObj.playNoteAt(this.xCoord, this.yCoord, this.zCoord, 1, this.field_35176_h);
		}

		this.field_35169_g = this.field_35168_f;
		float var1 = 0.1F;
		double var2;
		double var4;
		if(this.field_35176_h > 0 && this.field_35168_f == 0.0F && this.field_35172_b == null && this.field_35170_d == null) {
			var2 = (double)this.xCoord + 0.5D;
			var4 = (double)this.zCoord + 0.5D;
			if(this.field_35171_e != null) {
				var4 += 0.5D;
			}

			if(this.field_35173_c != null) {
				var2 += 0.5D;
			}

			this.worldObj.playSoundEffect(var2, (double)this.yCoord + 0.5D, var4, "random.door_open", 1.0F, this.worldObj.rand.nextFloat() * 0.1F + 0.9F);
		}

		if(this.field_35176_h == 0 && this.field_35168_f > 0.0F || this.field_35176_h > 0 && this.field_35168_f < 1.0F) {
			if(this.field_35176_h > 0) {
				this.field_35168_f += var1;
			} else {
				this.field_35168_f -= var1;
			}

			if(this.field_35168_f > 1.0F) {
				this.field_35168_f = 1.0F;
			}

			if(this.field_35168_f < 0.0F) {
				this.field_35168_f = 0.0F;
				if(this.field_35172_b == null && this.field_35170_d == null) {
					var2 = (double)this.xCoord + 0.5D;
					var4 = (double)this.zCoord + 0.5D;
					if(this.field_35171_e != null) {
						var4 += 0.5D;
					}

					if(this.field_35173_c != null) {
						var2 += 0.5D;
					}

					this.worldObj.playSoundEffect(var2, (double)this.yCoord + 0.5D, var4, "random.door_close", 1.0F, this.worldObj.rand.nextFloat() * 0.1F + 0.9F);
				}
			}
		}

	}

	public void func_35163_b(int var1, int var2) {
		if(var1 == 1) {
			this.field_35176_h = var2;
		}

	}

	public void func_35161_e() {
		++this.field_35176_h;
		this.worldObj.playNoteAt(this.xCoord, this.yCoord, this.zCoord, 1, this.field_35176_h);
	}

	public void func_35162_t_() {
		--this.field_35176_h;
		this.worldObj.playNoteAt(this.xCoord, this.yCoord, this.zCoord, 1, this.field_35176_h);
	}

	public void invalidate() {
		this.func_35164_g();
		this.func_35167_h();
		super.invalidate();
	}
}
