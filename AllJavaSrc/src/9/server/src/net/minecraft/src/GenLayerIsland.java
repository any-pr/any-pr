package net.minecraft.src;

public class GenLayerIsland extends GenLayer {
	public GenLayerIsland(long var1, GenLayer var3) {
		super(var1);
		this.field_35023_a = var3;
	}

	public int[] func_35018_a(int var1, int var2, int var3, int var4) {
		int var5 = var1 - 1;
		int var6 = var2 - 1;
		int var7 = var3 + 2;
		int var8 = var4 + 2;
		int[] var9 = this.field_35023_a.func_35018_a(var5, var6, var7, var8);
		int[] var10 = IntCache.func_35549_a(var3 * var4);

		for(int var11 = 0; var11 < var4; ++var11) {
			for(int var12 = 0; var12 < var3; ++var12) {
				int var13 = var9[var12 + 0 + (var11 + 0) * var7];
				int var14 = var9[var12 + 2 + (var11 + 0) * var7];
				int var15 = var9[var12 + 0 + (var11 + 2) * var7];
				int var16 = var9[var12 + 2 + (var11 + 2) * var7];
				int var17 = var9[var12 + 1 + (var11 + 1) * var7];
				this.func_35017_a((long)(var12 + var1), (long)(var11 + var2));
				if(var17 != 0 || var13 == 0 && var14 == 0 && var15 == 0 && var16 == 0) {
					if(var17 != 1 || var13 == 1 && var14 == 1 && var15 == 1 && var16 == 1) {
						var10[var12 + var11 * var3] = var17;
					} else {
						var10[var12 + var11 * var3] = 1 - this.func_35016_a(5) / 4;
					}
				} else {
					var10[var12 + var11 * var3] = 0 + this.func_35016_a(3) / 2;
				}
			}
		}

		return var10;
	}
}
