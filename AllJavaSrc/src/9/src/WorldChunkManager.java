package net.minecraft.src;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorldChunkManager {
	private GenLayer field_34903_b;
	private GenLayer field_34902_c;
	private GenLayer field_34901_d;
	private GenLayer field_35565_e;
	private BiomeCache field_35563_f;
	private List field_35564_g;
	public BiomeGenBase[] field_4195_d;

	protected WorldChunkManager() {
		this.field_35563_f = new BiomeCache(this);
		this.field_35564_g = new ArrayList();
		this.field_35564_g.add(BiomeGenBase.forest);
		this.field_35564_g.add(BiomeGenBase.swampland);
		this.field_35564_g.add(BiomeGenBase.taiga);
	}

	public WorldChunkManager(World var1) {
		this();
		GenLayer[] var2 = GenLayer.func_35497_a(var1.getRandomSeed());
		this.field_34903_b = var2[0];
		this.field_34902_c = var2[1];
		this.field_34901_d = var2[2];
		this.field_35565_e = var2[3];
	}

	public List func_35559_a() {
		return this.field_35564_g;
	}

	public BiomeGenBase getBiomeGenAtChunkCoord(ChunkCoordIntPair var1) {
		return this.getBiomeGenAt(var1.chunkXPos << 4, var1.chunkZPos << 4);
	}

	public BiomeGenBase getBiomeGenAt(int var1, int var2) {
		return this.field_35563_f.func_35725_a(var1, var2);
	}

	public float func_35558_c(int var1, int var2) {
		return this.field_35563_f.func_35727_c(var1, var2);
	}

	public float[] func_35560_b(float[] var1, int var2, int var3, int var4, int var5) {
		IntCache.func_35268_a();
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new float[var4 * var5];
		}

		int[] var6 = this.field_35565_e.func_35500_a(var2, var3, var4, var5);

		for(int var7 = 0; var7 < var4 * var5; ++var7) {
			float var8 = (float)var6[var7] / 65536.0F;
			if(var8 > 1.0F) {
				var8 = 1.0F;
			}

			var1[var7] = var8;
		}

		return var1;
	}

	public float func_35554_b(int var1, int var2) {
		return this.field_35563_f.func_35722_b(var1, var2);
	}

	public float[] getTemperatures(float[] var1, int var2, int var3, int var4, int var5) {
		IntCache.func_35268_a();
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new float[var4 * var5];
		}

		int[] var6 = this.field_34901_d.func_35500_a(var2, var3, var4, var5);

		for(int var7 = 0; var7 < var4 * var5; ++var7) {
			float var8 = (float)var6[var7] / 65536.0F;
			if(var8 > 1.0F) {
				var8 = 1.0F;
			}

			var1[var7] = var8;
		}

		return var1;
	}

	public BiomeGenBase[] func_35557_b(BiomeGenBase[] var1, int var2, int var3, int var4, int var5) {
		IntCache.func_35268_a();
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new BiomeGenBase[var4 * var5];
		}

		int[] var6 = this.field_34903_b.func_35500_a(var2, var3, var4, var5);

		for(int var7 = 0; var7 < var4 * var5; ++var7) {
			var1[var7] = BiomeGenBase.field_35486_a[var6[var7]];
		}

		return var1;
	}

	public BiomeGenBase[] func_4069_a(int var1, int var2, int var3, int var4) {
		if(var3 == 16 && var4 == 16 && (var1 & 15) == 0 && (var2 & 15) == 0) {
			return this.field_35563_f.func_35723_d(var1, var2);
		} else {
			this.field_4195_d = this.loadBlockGeneratorData(this.field_4195_d, var1, var2, var3, var4);
			return this.field_4195_d;
		}
	}

	public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] var1, int var2, int var3, int var4, int var5) {
		return this.func_35555_a(var1, var2, var3, var4, var5, true);
	}

	public BiomeGenBase[] func_35555_a(BiomeGenBase[] var1, int var2, int var3, int var4, int var5, boolean var6) {
		IntCache.func_35268_a();
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new BiomeGenBase[var4 * var5];
		}

		if(var6 && var4 == 16 && var5 == 16 && (var2 & 15) == 0 && (var3 & 15) == 0) {
			BiomeGenBase[] var9 = this.field_35563_f.func_35723_d(var2, var3);
			System.arraycopy(var9, 0, var1, 0, var4 * var5);
			return var1;
		} else {
			int[] var7 = this.field_34902_c.func_35500_a(var2, var3, var4, var5);

			for(int var8 = 0; var8 < var4 * var5; ++var8) {
				var1[var8] = BiomeGenBase.field_35486_a[var7[var8]];
			}

			return var1;
		}
	}

	public boolean func_35562_a(int var1, int var2, int var3, List var4) {
		int var5 = var1 - var3 >> 2;
		int var6 = var2 - var3 >> 2;
		int var7 = var1 + var3 >> 2;
		int var8 = var2 + var3 >> 2;
		int var9 = var7 - var5 + 1;
		int var10 = var8 - var6 + 1;
		int[] var11 = this.field_34903_b.func_35500_a(var5, var6, var9, var10);

		for(int var12 = 0; var12 < var9 * var10; ++var12) {
			BiomeGenBase var13 = BiomeGenBase.field_35486_a[var11[var12]];
			if(!var4.contains(var13)) {
				return false;
			}
		}

		return true;
	}

	public ChunkPosition func_35556_a(int var1, int var2, int var3, List var4, Random var5) {
		int var6 = var1 - var3 >> 2;
		int var7 = var2 - var3 >> 2;
		int var8 = var1 + var3 >> 2;
		int var9 = var2 + var3 >> 2;
		int var10 = var8 - var6 + 1;
		int var11 = var9 - var7 + 1;
		int[] var12 = this.field_34903_b.func_35500_a(var6, var7, var10, var11);
		ChunkPosition var13 = null;
		int var14 = 0;

		for(int var15 = 0; var15 < var12.length; ++var15) {
			int var16 = var6 + var15 % var10 << 2;
			int var17 = var7 + var15 / var10 << 2;
			BiomeGenBase var18 = BiomeGenBase.field_35486_a[var12[var15]];
			if(var4.contains(var18) && (var13 == null || var5.nextInt(var14 + 1) == 0)) {
				var13 = new ChunkPosition(var16, 0, var17);
				++var14;
			}
		}

		return var13;
	}

	public void func_35561_b() {
		this.field_35563_f.func_35724_a();
	}
}
