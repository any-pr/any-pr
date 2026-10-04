package net.minecraft.src;

import java.util.Random;

public class BlockMelon extends Block {
	protected BlockMelon(int var1) {
		super(var1, Material.pumpkin);
		this.blockIndexInTexture = 136;
	}

	public int getBlockTextureFromSideAndMetadata(int var1, int var2) {
		return var1 != 1 && var1 != 0 ? 136 : 137;
	}

	public int getBlockTextureFromSide(int var1) {
		return var1 != 1 && var1 != 0 ? 136 : 137;
	}

	public int idDropped(int var1, Random var2) {
		return Item.field_35416_bd.shiftedIndex;
	}

	public int quantityDropped(Random var1) {
		return 3 + var1.nextInt(5);
	}
}
