package net.minecraft.src;

public class BlockBreakable extends Block {
	private boolean localFlag;

	protected BlockBreakable(int var1, int var2, Material var3, boolean var4) {
		super(var1, var2, var3);
		this.localFlag = var4;
	}

	public boolean isOpaqueCube() {
		return false;
	}
}
