package net.minecraft.src;

public class GenLayerRiverMix extends GenLayer {
	private GenLayer field_35033_b;
	private GenLayer field_35034_c;

	public GenLayerRiverMix(long var1, GenLayer var3, GenLayer var4) {
		super(var1);
		this.field_35033_b = var3;
		this.field_35034_c = var4;
	}

	public void func_35015_b(long var1) {
		this.field_35033_b.func_35015_b(var1);
		this.field_35034_c.func_35015_b(var1);
		super.func_35015_b(var1);
	}

	public int[] func_35018_a(int var1, int var2, int var3, int var4) {
		int[] var5 = this.field_35033_b.func_35018_a(var1, var2, var3, var4);
		int[] var6 = this.field_35034_c.func_35018_a(var1, var2, var3, var4);
		int[] var7 = IntCache.func_35549_a(var3 * var4);

		for(int var8 = 0; var8 < var3 * var4; ++var8) {
			if(var5[var8] == BiomeGenBase.field_35519_b.field_35529_y) {
				var7[var8] = var5[var8];
			} else {
				var7[var8] = var6[var8] >= 0 ? var6[var8] : var5[var8];
			}
		}

		return var7;
	}
}
