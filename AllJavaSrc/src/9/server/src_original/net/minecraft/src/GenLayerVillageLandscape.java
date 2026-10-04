package net.minecraft.src;

public class GenLayerVillageLandscape extends GenLayer {
	private BiomeGenBase[] field_35029_b = new BiomeGenBase[]{BiomeGenBase.desert, BiomeGenBase.forest, BiomeGenBase.field_35518_e, BiomeGenBase.swampland, BiomeGenBase.field_35520_c, BiomeGenBase.taiga};

	public GenLayerVillageLandscape(long var1, GenLayer var3) {
		super(var1);
		this.field_35023_a = var3;
	}

	public int[] func_35018_a(int var1, int var2, int var3, int var4) {
		int[] var5 = this.field_35023_a.func_35018_a(var1, var2, var3, var4);
		int[] var6 = IntCache.func_35549_a(var3 * var4);

		for(int var7 = 0; var7 < var4; ++var7) {
			for(int var8 = 0; var8 < var3; ++var8) {
				this.func_35017_a((long)(var8 + var1), (long)(var7 + var2));
				var6[var8 + var7 * var3] = var5[var8 + var7 * var3] > 0 ? this.field_35029_b[this.func_35016_a(this.field_35029_b.length)].field_35529_y : 0;
			}
		}

		return var6;
	}
}
