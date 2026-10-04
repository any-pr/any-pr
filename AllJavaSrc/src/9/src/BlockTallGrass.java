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

	public int func_35274_i() {
		double var1 = 0.5D;
		double var3 = 1.0D;
		return ColorizerGrass.getGrassColor(var1, var3);
	}

	public int getRenderColor(int var1) {
		return var1 == 0 ? 16777215 : ColorizerFoliage.func_31073_c();
	}

	public int colorMultiplier(IBlockAccess var1, int var2, int var3, int var4) {
		int var5 = var1.getBlockMetadata(var2, var3, var4);
		if(var5 == 0) {
			return 16777215;
		} else {
			long var6 = (long)(var2 * 3129871 + var4 * 6129781 + var3);
			var6 = var6 * var6 * 42317861L + var6 * 11L;
			var2 = (int)((long)var2 + ((var6 >> 14 & 31L) - 16L));
			var3 = (int)((long)var3 + ((var6 >> 19 & 31L) - 16L));
			var4 = (int)((long)var4 + ((var6 >> 24 & 31L) - 16L));
			double var8 = (double)var1.getWorldChunkManager().func_35554_b(var2, var4);
			double var10 = (double)var1.getWorldChunkManager().func_35558_c(var2, var4);
			return ColorizerGrass.getGrassColor(var8, var10);
		}
	}

	public int idDropped(int var1, Random var2) {
		return var2.nextInt(8) == 0 ? Item.seeds.shiftedIndex : -1;
	}

	public void harvestBlock(World var1, EntityPlayer var2, int var3, int var4, int var5, int var6) {
		if(!var1.multiplayerWorld && var2.getCurrentEquippedItem() != null && var2.getCurrentEquippedItem().itemID == Item.shears.shiftedIndex) {
			var2.addStat(StatList.mineBlockStatArray[this.blockID], 1);
			this.dropBlockAsItem_do(var1, var3, var4, var5, new ItemStack(Block.tallGrass, 1, var6));
		} else {
			super.harvestBlock(var1, var2, var3, var4, var5, var6);
		}

	}
}
