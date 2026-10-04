package net.minecraft.src;

import java.util.ArrayList;

public class BlockPane extends Block {
	private int field_35300_a;

	protected BlockPane(int var1, int var2, int var3, Material var4) {
		super(var1, var2, var4);
		this.field_35300_a = var3;
	}

	public boolean isOpaqueCube() {
		return false;
	}

	public boolean renderAsNormalBlock() {
		return false;
	}

	public int getRenderType() {
		return 18;
	}

	public boolean shouldSideBeRendered(IBlockAccess var1, int var2, int var3, int var4, int var5) {
		int var6 = var1.getBlockId(var2, var3, var4);
		return var6 == this.blockID ? false : super.shouldSideBeRendered(var1, var2, var3, var4, var5);
	}

	public void getCollidingBoundingBoxes(World var1, int var2, int var3, int var4, AxisAlignedBB var5, ArrayList var6) {
		boolean var7 = this.func_35298_d(var1.getBlockId(var2, var3, var4 - 1));
		boolean var8 = this.func_35298_d(var1.getBlockId(var2, var3, var4 + 1));
		boolean var9 = this.func_35298_d(var1.getBlockId(var2 - 1, var3, var4));
		boolean var10 = this.func_35298_d(var1.getBlockId(var2 + 1, var3, var4));
		if((!var9 || !var10) && (var9 || var10 || var7 || var8)) {
			if(var9 && !var10) {
				this.setBlockBounds(0.0F, 0.0F, 7.0F / 16.0F, 0.5F, 1.0F, 9.0F / 16.0F);
				super.getCollidingBoundingBoxes(var1, var2, var3, var4, var5, var6);
			} else if(!var9 && var10) {
				this.setBlockBounds(0.5F, 0.0F, 7.0F / 16.0F, 1.0F, 1.0F, 9.0F / 16.0F);
				super.getCollidingBoundingBoxes(var1, var2, var3, var4, var5, var6);
			}
		} else {
			this.setBlockBounds(0.0F, 0.0F, 7.0F / 16.0F, 1.0F, 1.0F, 9.0F / 16.0F);
			super.getCollidingBoundingBoxes(var1, var2, var3, var4, var5, var6);
		}

		if((!var7 || !var8) && (var9 || var10 || var7 || var8)) {
			if(var7 && !var8) {
				this.setBlockBounds(7.0F / 16.0F, 0.0F, 0.0F, 9.0F / 16.0F, 1.0F, 0.5F);
				super.getCollidingBoundingBoxes(var1, var2, var3, var4, var5, var6);
			} else if(!var7 && var8) {
				this.setBlockBounds(7.0F / 16.0F, 0.0F, 0.5F, 9.0F / 16.0F, 1.0F, 1.0F);
				super.getCollidingBoundingBoxes(var1, var2, var3, var4, var5, var6);
			}
		} else {
			this.setBlockBounds(7.0F / 16.0F, 0.0F, 0.0F, 9.0F / 16.0F, 1.0F, 1.0F);
			super.getCollidingBoundingBoxes(var1, var2, var3, var4, var5, var6);
		}

	}

	public void setBlockBoundsForItemRender() {
		this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
	}

	public void setBlockBoundsBasedOnState(IBlockAccess var1, int var2, int var3, int var4) {
		float var5 = 7.0F / 16.0F;
		float var6 = 9.0F / 16.0F;
		float var7 = 7.0F / 16.0F;
		float var8 = 9.0F / 16.0F;
		boolean var9 = this.func_35298_d(var1.getBlockId(var2, var3, var4 - 1));
		boolean var10 = this.func_35298_d(var1.getBlockId(var2, var3, var4 + 1));
		boolean var11 = this.func_35298_d(var1.getBlockId(var2 - 1, var3, var4));
		boolean var12 = this.func_35298_d(var1.getBlockId(var2 + 1, var3, var4));
		if((!var11 || !var12) && (var11 || var12 || var9 || var10)) {
			if(var11 && !var12) {
				var5 = 0.0F;
			} else if(!var11 && var12) {
				var6 = 1.0F;
			}
		} else {
			var5 = 0.0F;
			var6 = 1.0F;
		}

		if((!var9 || !var10) && (var11 || var12 || var9 || var10)) {
			if(var9 && !var10) {
				var7 = 0.0F;
			} else if(!var9 && var10) {
				var8 = 1.0F;
			}
		} else {
			var7 = 0.0F;
			var8 = 1.0F;
		}

		this.setBlockBounds(var5, 0.0F, var7, var6, 1.0F, var8);
	}

	public int func_35299_s() {
		return this.field_35300_a;
	}

	public final boolean func_35298_d(int var1) {
		return Block.opaqueCubeLookup[var1] || var1 == this.blockID || var1 == Block.glass.blockID;
	}
}
