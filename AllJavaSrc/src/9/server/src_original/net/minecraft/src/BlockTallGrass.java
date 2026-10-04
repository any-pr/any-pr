package net.minecraft.src;

import java.util.Random;

public class BlockTallGrass extends BlockFlower {
	protected BlockTallGrass(int var1, int var2) {
		super(var1, var2);
		float var3 = 0.4F;
		this.setBlockBounds(0.5F - var3, 0.0F, 0.5F - var3, 0.5F + var3, 0.8F, 0.5F + var3);
	}

	public int getBlockTextureFromSideAndMetadata(int var1, int var2) {
		return var2 == 1 ? this.blockIndexInTexture : (var2 == 2 ? this.blockIndexInTexture + 16 + 1 : (var2 == 0 ? this.blockIndexInTexture + 16 : this.blockIndexInTexture));
	}

	public int idDropped(int var1, Random var2) {
		return var2.nextInt(8) == 0 ? Item.seeds.shiftedIndex : -1;
	}

	public void harvestBlock(World var1, EntityPlayer var2, int var3, int var4, int var5, int var6) {
		if(!var1.singleplayerWorld && var2.getCurrentEquippedItem() != null && var2.getCurrentEquippedItem().itemID == Item.shears.shiftedIndex) {
			var2.addStat(StatList.mineBlockStatArray[this.blockID], 1);
			this.dropBlockAsItem_do(var1, var3, var4, var5, new ItemStack(Block.tallGrass, 1, var6));
		} else {
			super.harvestBlock(var1, var2, var3, var4, var5, var6);
		}

	}
}
