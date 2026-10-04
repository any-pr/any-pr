package net.minecraft.src;

import java.util.Random;

public class BlockStem extends BlockFlower {
	private Block field_35068_a;

	protected BlockStem(int var1, Block var2) {
		super(var1, 111);
		this.field_35068_a = var2;
		this.setTickOnLoad(true);
		float var3 = 2.0F / 16.0F;
		this.setBlockBounds(0.5F - var3, 0.0F, 0.5F - var3, 0.5F + var3, 0.25F, 0.5F + var3);
	}

	protected boolean canThisPlantGrowOnThisBlockID(int var1) {
		return var1 == Block.tilledField.blockID;
	}

	public void updateTick(World var1, int var2, int var3, int var4, Random var5) {
		super.updateTick(var1, var2, var3, var4, var5);
		if(var1.getBlockLightValue(var2, var3 + 1, var4) >= 9) {
			float var6 = this.func_35067_h(var1, var2, var3, var4);
			if(var5.nextInt((int)(100.0F / var6)) == 0) {
				int var7 = var1.getBlockMetadata(var2, var3, var4);
				if(var7 < 7) {
					++var7;
					var1.setBlockMetadataWithNotify(var2, var3, var4, var7);
				} else {
					if(var1.getBlockId(var2 - 1, var3, var4) == this.field_35068_a.blockID) {
						return;
					}

					if(var1.getBlockId(var2 + 1, var3, var4) == this.field_35068_a.blockID) {
						return;
					}

					if(var1.getBlockId(var2, var3, var4 - 1) == this.field_35068_a.blockID) {
						return;
					}

					if(var1.getBlockId(var2, var3, var4 + 1) == this.field_35068_a.blockID) {
						return;
					}

					int var8 = var5.nextInt(4);
					int var9 = var2;
					int var10 = var4;
					if(var8 == 0) {
						var9 = var2 - 1;
					}

					if(var8 == 1) {
						++var9;
					}

					if(var8 == 2) {
						var10 = var4 - 1;
					}

					if(var8 == 3) {
						++var10;
					}

					if(var1.getBlockId(var9, var3, var10) == 0 && var1.getBlockId(var9, var3 - 1, var10) == Block.tilledField.blockID) {
						var1.setBlockWithNotify(var9, var3, var10, this.field_35068_a.blockID);
					}
				}
			}
		}

	}

	public void func_35066_f_(World var1, int var2, int var3, int var4) {
		var1.setBlockMetadataWithNotify(var2, var3, var4, 7);
	}

	private float func_35067_h(World var1, int var2, int var3, int var4) {
		float var5 = 1.0F;
		int var6 = var1.getBlockId(var2, var3, var4 - 1);
		int var7 = var1.getBlockId(var2, var3, var4 + 1);
		int var8 = var1.getBlockId(var2 - 1, var3, var4);
		int var9 = var1.getBlockId(var2 + 1, var3, var4);
		int var10 = var1.getBlockId(var2 - 1, var3, var4 - 1);
		int var11 = var1.getBlockId(var2 + 1, var3, var4 - 1);
		int var12 = var1.getBlockId(var2 + 1, var3, var4 + 1);
		int var13 = var1.getBlockId(var2 - 1, var3, var4 + 1);
		boolean var14 = var8 == this.blockID || var9 == this.blockID;
		boolean var15 = var6 == this.blockID || var7 == this.blockID;
		boolean var16 = var10 == this.blockID || var11 == this.blockID || var12 == this.blockID || var13 == this.blockID;

		for(int var17 = var2 - 1; var17 <= var2 + 1; ++var17) {
			for(int var18 = var4 - 1; var18 <= var4 + 1; ++var18) {
				int var19 = var1.getBlockId(var17, var3 - 1, var18);
				float var20 = 0.0F;
				if(var19 == Block.tilledField.blockID) {
					var20 = 1.0F;
					if(var1.getBlockMetadata(var17, var3 - 1, var18) > 0) {
						var20 = 3.0F;
					}
				}

				if(var17 != var2 || var18 != var4) {
					var20 /= 4.0F;
				}

				var5 += var20;
			}
		}

		if(var16 || var14 && var15) {
			var5 /= 2.0F;
		}

		return var5;
	}

	public int getBlockTextureFromSideAndMetadata(int var1, int var2) {
		return this.blockIndexInTexture;
	}

	public void setBlockBoundsBasedOnState(IBlockAccess var1, int var2, int var3, int var4) {
		this.maxY = (double)((float)(var1.getBlockMetadata(var2, var3, var4) * 2 + 2) / 16.0F);
		float var5 = 2.0F / 16.0F;
		this.setBlockBounds(0.5F - var5, 0.0F, 0.5F - var5, 0.5F + var5, (float)this.maxY, 0.5F + var5);
	}

	public void dropBlockAsItemWithChance(World var1, int var2, int var3, int var4, int var5, float var6) {
		super.dropBlockAsItemWithChance(var1, var2, var3, var4, var5, var6);
		if(!var1.singleplayerWorld) {
			Item var7 = null;
			if(this.field_35068_a == Block.pumpkin) {
				var7 = Item.field_35417_be;
			}

			if(this.field_35068_a == Block.field_35048_bs) {
				var7 = Item.field_35412_bf;
			}

			for(int var8 = 0; var8 < 3; ++var8) {
				if(var1.rand.nextInt(15) <= var5) {
					float var9 = 0.7F;
					float var10 = var1.rand.nextFloat() * var9 + (1.0F - var9) * 0.5F;
					float var11 = var1.rand.nextFloat() * var9 + (1.0F - var9) * 0.5F;
					float var12 = var1.rand.nextFloat() * var9 + (1.0F - var9) * 0.5F;
					EntityItem var13 = new EntityItem(var1, (double)((float)var2 + var10), (double)((float)var3 + var11), (double)((float)var4 + var12), new ItemStack(var7));
					var13.delayBeforeCanPickup = 10;
					var1.entityJoinedWorld(var13);
				}
			}

		}
	}

	public int idDropped(int var1, Random var2) {
		if(var1 == 7) {
		}

		return -1;
	}

	public int quantityDropped(Random var1) {
		return 1;
	}
}
