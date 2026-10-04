package net.minecraft.src;

public class GenLayerRiverMix extends GenLayer {
	private GenLayer field_35512_b;
	private GenLayer field_35513_c;

	public GenLayerRiverMix(long var1, GenLayer var3, GenLayer var4) {
		super(var1);
		this.field_35512_b = var3;
		this.field_35513_c = var4;
	}

	public void func_35496_b(long var1) {
		this.field_35512_b.func_35496_b(var1);
		this.field_35513_c.func_35496_b(var1);
		super.func_35496_b(var1);
	}

	public int[] func_35500_a(int var1, int var2, int var3, int var4) {
		int[] var5 = this.field_35512_b.func_35500_a(var1, var2, var3, var4);
		int[] var6 = this.field_35513_c.func_35500_a(var1, var2, var3, var4);
		int[] var7 = IntCache.func_35267_a(var3 * var4);

		for(int var8 = 0; var8 < var3 * var4; ++var8) {
			if(var5[var8] == BiomeGenBase.field_35484_b.field_35494_y) {
				var7[var8] = var5[var8];
			} else {
				var7[var8] = var6[var8] >= 0 ? var6[var8] : var5[var8];
			}
		}

		return var7;
	}
}
