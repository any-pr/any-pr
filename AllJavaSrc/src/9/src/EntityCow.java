package net.minecraft.src;

public class EntityCow extends EntityAnimal {
	public EntityCow(World var1) {
		super(var1);
		this.texture = "/mob/cow.png";
		this.setSize(0.9F, 1.3F);
	}

	public void writeEntityToNBT(NBTTagCompound var1) {
		super.writeEntityToNBT(var1);
	}

	public void readEntityFromNBT(NBTTagCompound var1) {
		super.readEntityFromNBT(var1);
	}

	protected String getLivingSound() {
		return "mob.cow";
	}

	protected String getHurtSound() {
		return "mob.cowhurt";
	}

	protected String getDeathSound() {
		return "mob.cowhurt";
	}

	protected float getSoundVolume() {
		return 0.4F;
	}

	protected int getDropItemId() {
		return Item.leather.shiftedIndex;
	}

	protected void a(boolean var1) {
		int var2 = this.rand.nextInt(3);

		int var3;
		for(var3 = 0; var3 < var2; ++var3) {
			this.dropItem(Item.leather.shiftedIndex, 1);
		}

		var2 = this.rand.nextInt(3) + 1;

		for(var3 = 0; var3 < var2; ++var3) {
			if(this.fire > 0) {
				this.dropItem(Item.field_35418_bk.shiftedIndex, 1);
			} else {
				this.dropItem(Item.field_35417_bj.shiftedIndex, 1);
			}
		}

	}

	public boolean interact(EntityPlayer var1) {
		ItemStack var2 = var1.inventory.getCurrentItem();
		if(var2 != null && var2.itemID == Item.bucketEmpty.shiftedIndex) {
			var1.inventory.setInventorySlotContents(var1.inventory.currentItem, new ItemStack(Item.bucketMilk));
			return true;
		} else {
			return false;
		}
	}
}
