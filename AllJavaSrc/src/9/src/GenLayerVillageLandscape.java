package net.minecraft.src;

public class GenLayerVillageLandscape extends GenLayer {
	private BiomeGenBase[] field_35509_b = new BiomeGenBase[]{BiomeGenBase.desert, BiomeGenBase.forest, BiomeGenBase.field_35483_e, BiomeGenBase.swampland, BiomeGenBase.field_35485_c, BiomeGenBase.taiga};

	public GenLayerVillageLandscape(long var1, GenLayer var3) {
		super(var1);
		this.field_35504_a = var3;
	}

	public int[] func_35500_a(int var1, int var2, int var3, int var4) {
		int[] var5 = this.field_35504_a.func_35500_a(var1, var2, var3, var4);
		int[] var6 = IntCache.func_35267_a(var3 * var4);

		for(int var7 = 0; var7 < var4; ++var7) {
			for(int var8 = 0; var8 < var3; ++var8) {
				this.func_35499_a((long)(var8 + var1), (long)(var7 + var2));
				var6[var8 + var7 * var3] = var5[var8 + var7 * var3] > 0 ? this.field_35509_b[this.func_35498_a(this.field_35509_b.length)].field_35494_y : 0;
			}
		}

		return var6;
	}
}
