package net.minecraft.src;

import java.util.Random;

public class BlockMushroomCap extends Block {
	private int field_35292_a;

	public BlockMushroomCap(int var1, Material var2, int var3, int var4) {
		super(var1, var3, var2);
		this.field_35292_a = var4;
	}

	public int getBlockTextureFromSideAndMetadata(int var1, int var2) {
		return var2 == 10 && var1 > 1 ? this.blockIndexInTexture - 1 : (var2 >= 1 && var2 <= 9 && var1 == 1 ? this.blockIndexInTexture - 16 - this.field_35292_a : (var2 >= 1 && var2 <= 3 && var1 == 2 ? this.blockIndexInTexture - 16 - this.field_35292_a : (var2 >= 7 && var2 <= 9 && var1 == 3 ? this.blockIndexInTexture - 16 - this.field_35292_a : ((var2 == 1 || var2 == 4 || var2 == 7) && var1 == 4 ? this.blockIndexInTexture - 16 - this.field_35292_a : ((var2 == 3 || var2 == 6 || var2 == 9) && var1 == 5 ? this.blockIndexInTexture - 16 - this.field_35292_a : this.blockIndexInTexture)))));
	}

	public int quantityDropped(Random var1) {
		int var2 = var1.nextInt(10) - 7;
		if(var2 < 0) {
			var2 = 0;
		}

		return var2;
	}

	public int idDropped(int var1, Random var2) {
		return Block.mushroomBrown.blockID + this.field_35292_a;
	}
}
