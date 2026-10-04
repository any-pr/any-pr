package net.minecraft.src;

public class ItemVine extends ItemBlock {
	private final Block field_35421_a = Block.blocksList[this.func_35419_a()];

	public ItemVine(int var1, boolean var2) {
		super(var1);
		if(var2) {
			this.setMaxDamage(0);
			this.setHasSubtypes(true);
		}

	}

	public int getMetadata(int var1) {
		return var1;
	}
}
