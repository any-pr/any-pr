package net.minecraft.src;

public class ItemBow extends Item {
	public ItemBow(int var1) {
		super(var1);
		this.maxStackSize = 1;
	}

	public void func_35414_a(ItemStack var1, World var2, EntityPlayer var3, int var4) {
		if(var3.inventory.func_35157_d(Item.arrow.shiftedIndex)) {
			int var5 = this.func_35411_c(var1) - var4;
			float var6 = (float)var5 / 20.0F;
			var6 = (var6 * var6 + var6 * 2.0F) / 3.0F;
			if((double)var6 < 0.1D) {
				return;
			}

			if(var6 > 1.0F) {
				var6 = 1.0F;
			}

			EntityArrow var7 = new EntityArrow(var2, var3, var6 * 2.0F);
			if(var6 == 1.0F) {
				var7.field_35140_d = true;
			}

			var2.playSoundAtEntity(var3, "random.bow", 1.0F, 1.0F / (itemRand.nextFloat() * 0.4F + 1.2F) + var6 * 0.5F);
			var3.inventory.consumeInventoryItem(Item.arrow.shiftedIndex);
			if(!var2.multiplayerWorld) {
				var2.entityJoinedWorld(var7);
			}
		}

	}

	public ItemStack func_35413_b(ItemStack var1, World var2, EntityPlayer var3) {
		return var1;
	}

	public int func_35411_c(ItemStack var1) {
		return 72000;
	}

	public EnumAction func_35412_b(ItemStack var1) {
		return EnumAction.bow;
	}

	public ItemStack onItemRightClick(ItemStack var1, World var2, EntityPlayer var3) {
		if(var3.inventory.func_35157_d(Item.arrow.shiftedIndex)) {
			var3.func_35199_b(var1, this.func_35411_c(var1));
		}

		return var1;
	}
}
