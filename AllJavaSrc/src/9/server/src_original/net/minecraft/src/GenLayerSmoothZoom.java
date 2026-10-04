package net.minecraft.src;

public class GenLayerSmoothZoom extends GenLayer {
	public GenLayerSmoothZoom(long var1, GenLayer var3) {
		super(var1);
		this.field_35023_a = var3;
	}

	public int[] func_35018_a(int var1, int var2, int var3, int var4) {
		int var5 = var1 >> 1;
		int var6 = var2 >> 1;
		int var7 = (var3 >> 1) + 3;
		int var8 = (var4 >> 1) + 3;
		int[] var9 = this.field_35023_a.func_35018_a(var5, var6, var7, var8);
		int[] var10 = IntCache.func_35549_a(var7 * 2 * var8 * 2);
		int var11 = var7 << 1;

		int var13;
		for(int var12 = 0; var12 < var8 - 1; ++var12) {
			var13 = var12 << 1;
			int var14 = var13 * var11;
			int var15 = var9[0 + (var12 + 0) * var7];
			int var16 = var9[0 + (var12 + 1) * var7];

			for(int var17 = 0; var17 < var7 - 1; ++var17) {
				this.func_35017_a((long)(var17 + var5 << 1), (long)(var12 + var6 << 1));
				int var18 = var9[var17 + 1 + (var12 + 0) * var7];
				int var19 = var9[var17 + 1 + (var12 + 1) * var7];
				var10[var14] = var15;
				var10[var14++ + var11] = var15 + (var16 - var15) * this.func_35016_a(256) / 256;
				var10[var14] = var15 + (var18 - var15) * this.func_35016_a(256) / 256;
				int var20 = var15 + (var18 - var15) * this.func_35016_a(256) / 256;
				int var21 = var16 + (var19 - var16) * this.func_35016_a(256) / 256;
				var10[var14++ + var11] = var20 + (var21 - var20) * this.func_35016_a(256) / 256;
				var15 = var18;
				var16 = var19;
			}
		}

		int[] var22 = IntCache.func_35549_a(var3 * var4);

		for(var13 = 0; var13 < var4; ++var13) {
			System.arraycopy(var10, (var13 + (var2 & 1)) * (var7 << 1) + (var1 & 1), var22, var13 * var3, var3);
		}

		return var22;
	}

	public static GenLayer func_35030_a(long var0, GenLayer var2, int var3) {
		Object var4 = var2;

		for(int var5 = 0; var5 < var3; ++var5) {
			var4 = new GenLayerSmoothZoom(var0 + (long)var5, (GenLayer)var4);
		}

		return (GenLayer)var4;
	}
}
