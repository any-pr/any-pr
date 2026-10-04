package net.minecraft.src;

import java.util.Random;

public class BlockSilverfish extends Block {
	public BlockSilverfish(int var1) {
		super(var1, 1, Material.clay);
		this.setHardness(0.0F);
	}

	public void harvestBlock(World var1, EntityPlayer var2, int var3, int var4, int var5, int var6) {
		super.harvestBlock(var1, var2, var3, var4, var5, var6);
	}

	public int getBlockTextureFromSideAndMetadata(int var1, int var2) {
		return var2 == 1 ? Block.cobblestone.blockIndexInTexture : (var2 == 2 ? Block.field_35285_bn.blockIndexInTexture : Block.stone.blockIndexInTexture);
	}

	public void onBlockDestroyedByPlayer(World var1, int var2, int var3, int var4, int var5) {
		if(!var1.multiplayerWorld) {
			EntitySilverfish var6 = new EntitySilverfish(var1);
			var6.setLocationAndAngles((double)var2 + 0.5D, (double)var3, (double)var4 + 0.5D, 0.0F, 0.0F);
			var1.entityJoinedWorld(var6);
			var6.spawnExplosionParticle();
		}

		super.onBlockDestroyedByPlayer(var1, var2, var3, var4, var5);
	}

	public int quantityDropped(Random var1) {
		return 0;
	}

	public static boolean func_35305_d(int var0) {
		return var0 == Block.stone.blockID || var0 == Block.cobblestone.blockID || var0 == Block.field_35285_bn.blockID;
	}

	public static int func_35304_f(int var0) {
		return var0 == Block.cobblestone.blockID ? 1 : (var0 == Block.field_35285_bn.blockID ? 2 : 0);
	}
}
