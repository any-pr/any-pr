package net.minecraft.src;

public class GenLayerTemperature extends GenLayer {
	public GenLayerTemperature(GenLayer var1) {
		super(0L);
		this.field_35504_a = var1;
	}

	public int[] func_35500_a(int var1, int var2, int var3, int var4) {
		int[] var5 = this.field_35504_a.func_35500_a(var1, var2, var3, var4);
		int[] var6 = IntCache.func_35267_a(var3 * var4);

		for(int var7 = 0; var7 < var3 * var4; ++var7) {
			var6[var7] = BiomeGenBase.field_35486_a[var5[var7]].func_35474_f();
		}

		return var6;
	}
}
