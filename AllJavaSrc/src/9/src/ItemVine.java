package net.minecraft.src;

public class ItemVine extends ItemBlock {
	private final Block field_35436_a = Block.blocksList[this.func_35435_b()];

	public ItemVine(int var1, boolean var2) {
		super(var1);
		if(var2) {
			this.setMaxDamage(0);
			this.setHasSubtypes(true);
		}

	}

	public int getColorFromDamage(int var1) {
		return this.field_35436_a.func_35274_i();
	}

	public int getIconFromDamage(int var1) {
		return this.field_35436_a.getBlockTextureFromSideAndMetadata(0, var1);
	}

	public int getPlacedBlockMetadata(int var1) {
		return var1;
	}
}
