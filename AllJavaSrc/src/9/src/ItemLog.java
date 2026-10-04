package net.minecraft.src;

public class ItemLog extends ItemBlock {
	private Block field_35437_a;

	public ItemLog(int var1, Block var2) {
		super(var1);
		this.field_35437_a = var2;
		this.setMaxDamage(0);
		this.setHasSubtypes(true);
	}

	public int getIconFromDamage(int var1) {
		return this.field_35437_a.getBlockTextureFromSideAndMetadata(2, var1);
	}

	public int getPlacedBlockMetadata(int var1) {
		return var1;
	}
}
