package net.minecraft.src;

public class ItemSlab extends ItemBlock {
	public ItemSlab(int var1) {
		super(var1);
		this.setMaxDamage(0);
		this.setHasSubtypes(true);
	}

	public int getMetadata(int var1) {
		return var1;
	}

	public String func_35407_a(ItemStack var1) {
		int var2 = var1.getItemDamage();
		if(var2 < 0 || var2 >= BlockStep.field_35062_a.length) {
			var2 = 0;
		}

		return super.getItemName() + "." + BlockStep.field_35062_a[var2];
	}
}
