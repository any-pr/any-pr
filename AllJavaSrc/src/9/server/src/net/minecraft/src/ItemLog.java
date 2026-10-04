package net.minecraft.src;

public class ItemLog extends ItemBlock {
	private Block field_35420_a;

	public ItemLog(int var1, Block var2) {
		super(var1);
		this.field_35420_a = var2;
		this.setMaxDamage(0);
		this.setHasSubtypes(true);
	}

	public int getMetadata(int var1) {
		return var1;
	}
}
