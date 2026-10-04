package net.minecraft.src;

import java.util.ArrayList;
import java.util.List;

public class BiomeCache {
	private final WorldChunkManager field_35687_a;
	private long field_35685_b = 0L;
	private PlayerHash field_35686_c = new PlayerHash();
	private List field_35684_d = new ArrayList();

	public BiomeCache(WorldChunkManager var1) {
		this.field_35687_a = var1;
	}

	private BiomeCacheBlock func_35680_c(int var1, int var2) {
		var1 >>= 4;
		var2 >>= 4;
		long var3 = (long)var1 & 4294967295L | ((long)var2 & 4294967295L) << 32;
		BiomeCacheBlock var5 = (BiomeCacheBlock)this.field_35686_c.getValueByKey(var3);
		if(var5 == null) {
			var5 = new BiomeCacheBlock(this, var1, var2);
			this.field_35686_c.add(var3, var5);
			this.field_35684_d.add(var5);
		}

		var5.field_35701_f = System.currentTimeMillis();
		return var5;
	}

	public BiomeGenBase func_35683_a(int var1, int var2) {
		return this.func_35680_c(var1, var2).func_35700_a(var1, var2);
	}

	public void func_35681_a() {
		long var1 = System.currentTimeMillis();
		long var3 = var1 - this.field_35685_b;
		if(var3 > 7500L || var3 < 0L) {
			this.field_35685_b = var1;

			for(int var5 = 0; var5 < this.field_35684_d.size(); ++var5) {
				BiomeCacheBlock var6 = (BiomeCacheBlock)this.field_35684_d.get(var5);
				long var7 = var1 - var6.field_35701_f;
				if(var7 > 30000L || var7 < 0L) {
					this.field_35684_d.remove(var5--);
					long var9 = (long)var6.field_35703_d & 4294967295L | ((long)var6.field_35704_e & 4294967295L) << 32;
					this.field_35686_c.remove(var9);
				}
			}
		}

	}

	public BiomeGenBase[] func_35682_b(int var1, int var2) {
		return this.func_35680_c(var1, var2).field_35706_c;
	}

	static WorldChunkManager func_35679_a(BiomeCache var0) {
		return var0.field_35687_a;
	}
}
