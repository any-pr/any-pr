package net.minecraft.src;

class BiomeCacheBlock {
	public float[] field_35707_a;
	public float[] field_35705_b;
	public BiomeGenBase[] field_35706_c;
	public int field_35703_d;
	public int field_35704_e;
	public long field_35701_f;
	final BiomeCache field_35702_g;

	public BiomeCacheBlock(BiomeCache var1, int var2, int var3) {
		this.field_35702_g = var1;
		this.field_35707_a = new float[256];
		this.field_35705_b = new float[256];
		this.field_35706_c = new BiomeGenBase[256];
		this.field_35703_d = var2;
		this.field_35704_e = var3;
		BiomeCache.func_35679_a(var1).getTemperatures(this.field_35707_a, var2 << 4, var3 << 4, 16, 16);
		BiomeCache.func_35679_a(var1).func_4065_a(this.field_35705_b, var2 << 4, var3 << 4, 16, 16);
		BiomeCache.func_35679_a(var1).func_35140_a(this.field_35706_c, var2 << 4, var3 << 4, 16, 16, false);
	}

	public BiomeGenBase func_35700_a(int var1, int var2) {
		return this.field_35706_c[var1 & 15 | (var2 & 15) << 4];
	}
}
