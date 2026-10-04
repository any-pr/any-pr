package net.minecraft.src;

class BiomeCacheBlock {
	public float[] field_35659_a;
	public float[] field_35657_b;
	public BiomeGenBase[] field_35658_c;
	public int field_35655_d;
	public int field_35656_e;
	public long field_35653_f;
	final BiomeCache field_35654_g;

	public BiomeCacheBlock(BiomeCache var1, int var2, int var3) {
		this.field_35654_g = var1;
		this.field_35659_a = new float[256];
		this.field_35657_b = new float[256];
		this.field_35658_c = new BiomeGenBase[256];
		this.field_35655_d = var2;
		this.field_35656_e = var3;
		BiomeCache.func_35721_a(var1).getTemperatures(this.field_35659_a, var2 << 4, var3 << 4, 16, 16);
		BiomeCache.func_35721_a(var1).func_35560_b(this.field_35657_b, var2 << 4, var3 << 4, 16, 16);
		BiomeCache.func_35721_a(var1).func_35555_a(this.field_35658_c, var2 << 4, var3 << 4, 16, 16, false);
	}

	public BiomeGenBase func_35651_a(int var1, int var2) {
		return this.field_35658_c[var1 & 15 | (var2 & 15) << 4];
	}

	public float func_35650_b(int var1, int var2) {
		return this.field_35659_a[var1 & 15 | (var2 & 15) << 4];
	}

	public float func_35652_c(int var1, int var2) {
		return this.field_35657_b[var1 & 15 | (var2 & 15) << 4];
	}
}
