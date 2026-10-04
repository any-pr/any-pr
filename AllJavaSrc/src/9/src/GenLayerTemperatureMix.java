package net.minecraft.src;

public class GenLayerTemperatureMix extends GenLayer {
	private GenLayer field_35505_b;
	private int field_35506_c;

	public GenLayerTemperatureMix(GenLayer var1, GenLayer var2, int var3) {
		super(0L);
		this.field_35504_a = var2;
		this.field_35505_b = var1;
		this.field_35506_c = var3;
	}

	public int[] func_35500_a(int var1, int var2, int var3, int var4) {
		int[] var5 = this.field_35504_a.func_35500_a(var1, var2, var3, var4);
		int[] var6 = this.field_35505_b.func_35500_a(var1, var2, var3, var4);
		int[] var7 = IntCache.func_35267_a(var3 * var4);

		for(int var8 = 0; var8 < var3 * var4; ++var8) {
			var7[var8] = var6[var8] + (BiomeGenBase.field_35486_a[var5[var8]].func_35474_f() - var6[var8]) / (this.field_35506_c * 2 + 1);
		}

		return var7;
	}
}
