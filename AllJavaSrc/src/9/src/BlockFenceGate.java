package net.minecraft.src;

public class BlockFenceGate extends Block {
	public BlockFenceGate(int var1, int var2) {
		super(var1, var2, Material.wood);
	}

	public boolean canPlaceBlockAt(World var1, int var2, int var3, int var4) {
		return !var1.getBlockMaterial(var2, var3 - 1, var4).isSolid() ? false : super.canPlaceBlockAt(var1, var2, var3, var4);
	}

	public AxisAlignedBB getCollisionBoundingBoxFromPool(World var1, int var2, int var3, int var4) {
		int var5 = var1.getBlockMetadata(var2, var3, var4);
		return func_35291_d(var5) ? null : AxisAlignedBB.getBoundingBoxFromPool((double)var2, (double)var3, (double)var4, (double)(var2 + 1), (double)((float)var3 + 1.5F), (double)(var4 + 1));
	}

	public boolean isOpaqueCube() {
		return false;
	}

	public boolean renderAsNormalBlock() {
		return false;
	}

	public int getRenderType() {
		return 21;
	}

	public void onBlockPlacedBy(World var1, int var2, int var3, int var4, EntityLiving var5) {
		int var6 = (MathHelper.floor_double((double)(var5.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3) % 4;
		var1.setBlockMetadataWithNotify(var2, var3, var4, var6);
	}

	public boolean blockActivated(World var1, int var2, int var3, int var4, EntityPlayer var5) {
		int var6 = var1.getBlockMetadata(var2, var3, var4);
		if(func_35291_d(var6)) {
			var1.setBlockMetadataWithNotify(var2, var3, var4, var6 & -5);
		} else {
			int var7 = (MathHelper.floor_double((double)(var5.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3) % 4;
			int var8 = func_35290_f(var6);
			if(var8 == (var7 + 2) % 4) {
				var6 = var7;
			}

			var1.setBlockMetadataWithNotify(var2, var3, var4, var6 | 4);
		}

		var1.playAuxSFXAtEntity(var5, 1003, var2, var3, var4, 0);
		return true;
	}

	public static boolean func_35291_d(int var0) {
		return (var0 & 4) != 0;
	}

	public static int func_35290_f(int var0) {
		return var0 & 3;
	}
}
