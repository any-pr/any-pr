package net.minecraft.src;

public abstract class BlockContainer extends Block {
	protected BlockContainer(int var1, Material var2) {
		super(var1, var2);
		isBlockContainer[this.blockID] = true;
	}

	protected BlockContainer(int var1, int var2, Material var3) {
		super(var1, var2, var3);
		isBlockContainer[this.blockID] = true;
	}

	public void onBlockAdded(World var1, int var2, int var3, int var4) {
		super.onBlockAdded(var1, var2, var3, var4);
		var1.setBlockTileEntity(var2, var3, var4, this.getBlockEntity());
	}

	public void onBlockRemoval(World var1, int var2, int var3, int var4) {
		super.onBlockRemoval(var1, var2, var3, var4);
		var1.removeBlockTileEntity(var2, var3, var4);
	}

	public abstract TileEntity getBlockEntity();

	public void playBlock(World var1, int var2, int var3, int var4, int var5, int var6) {
		super.playBlock(var1, var2, var3, var4, var5, var6);
		TileEntity var7 = var1.getBlockTileEntity(var2, var3, var4);
		if(var7 != null) {
			var7.func_35143_b(var5, var6);
		}

	}
}
