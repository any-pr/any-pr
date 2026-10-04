package net.minecraft.src;

public class BlockStoneBrick extends Block {
	public BlockStoneBrick(int var1) {
		super(var1, 54, Material.rock);
	}

	public int getBlockTextureFromSideAndMetadata(int var1, int var2) {
		switch(var2) {
		case 1:
			return 100;
		case 2:
			return 101;
		default:
			return 54;
		}
	}

	protected int damageDropped(int var1) {
		return var1;
	}
}
