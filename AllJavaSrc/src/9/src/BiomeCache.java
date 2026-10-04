package net.minecraft.src;

import java.util.ArrayList;
import java.util.List;

public class BiomeCache {
	private final WorldChunkManager field_35731_a;
	private long field_35729_b = 0L;
	private PlayerList field_35730_c = new PlayerList();
	private List field_35728_d = new ArrayList();

	public BiomeCache(WorldChunkManager var1) {
		this.field_35731_a = var1;
	}

	private BiomeCacheBlock func_35726_e(int var1, int var2) {
		var1 >>= 4;
		var2 >>= 4;
		long var3 = (long)var1 & 4294967295L | ((long)var2 & 4294967295L) << 32;
		BiomeCacheBlock var5 = (BiomeCacheBlock)this.field_35730_c.func_35578_a(var3);
		if(var5 == null) {
			var5 = new BiomeCacheBlock(this, var1, var2);
			this.field_35730_c.func_35577_a(var3, var5);
			this.field_35728_d.add(var5);
		}

		var5.field_35653_f = System.currentTimeMillis();
		return var5;
	}

	public BiomeGenBase func_35725_a(int var1, int var2) {
		return this.func_35726_e(var1, var2).func_35651_a(var1, var2);
	}

	public float func_35722_b(int var1, int var2) {
		return this.func_35726_e(var1, var2).func_35650_b(var1, var2);
	}

	public float func_35727_c(int var1, int var2) {
		return this.func_35726_e(var1, var2).func_35652_c(var1, var2);
	}

	public void func_35724_a() {
		long var1 = System.currentTimeMillis();
		long var3 = var1 - this.field_35729_b;
		if(var3 > 7500L || var3 < 0L) {
			this.field_35729_b = var1;

			for(int var5 = 0; var5 < this.field_35728_d.size(); ++var5) {
				BiomeCacheBlock var6 = (BiomeCacheBlock)this.field_35728_d.get(var5);
				long var7 = var1 - var6.field_35653_f;
				if(var7 > 30000L || var7 < 0L) {
					this.field_35728_d.remove(var5--);
					long var9 = (long)var6.field_35655_d & 4294967295L | ((long)var6.field_35656_e & 4294967295L) << 32;
					this.field_35730_c.func_35574_d(var9);
				}
			}
		}

	}

	public BiomeGenBase[] func_35723_d(int var1, int var2) {
		return this.func_35726_e(var1, var2).field_35658_c;
	}

	static WorldChunkManager func_35721_a(BiomeCache var0) {
		return var0.field_35731_a;
	}
}
