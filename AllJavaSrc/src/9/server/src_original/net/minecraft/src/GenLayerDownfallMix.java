package net.minecraft.src;

public class GenLayerDownfallMix extends GenLayer {
	private GenLayer field_35035_b;
	private int field_35036_c;

	public GenLayerDownfallMix(GenLayer var1, GenLayer var2, int var3) {
		super(0L);
		this.field_35023_a = var2;
		this.field_35035_b = var1;
		this.field_35036_c = var3;
	}

	public int[] func_35018_a(int var1, int var2, int var3, int var4) {
		int[] var5 = this.field_35023_a.func_35018_a(var1, var2, var3, var4);
		int[] var6 = this.field_35035_b.func_35018_a(var1, var2, var3, var4);
		int[] var7 = IntCache.func_35549_a(var3 * var4);

		for(int var8 = 0; var8 < var3 * var4; ++var8) {
			var7[var8] = var6[var8] + (BiomeGenBase.field_35521_a[var5[var8]].func_35510_e() - var6[var8]) / (this.field_35036_c + 1);
		}

		return var7;
	}
}
