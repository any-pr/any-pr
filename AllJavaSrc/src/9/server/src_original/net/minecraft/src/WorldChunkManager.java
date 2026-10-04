package net.minecraft.src;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorldChunkManager {
	private GenLayer field_34907_a;
	private GenLayer field_34906_b;
	private GenLayer field_34905_c;
	private GenLayer field_35144_d;
	private BiomeCache field_35145_e;
	private List field_35143_f;

	protected WorldChunkManager() {
		this.field_35145_e = new BiomeCache(this);
		this.field_35143_f = new ArrayList();
		this.field_35143_f.add(BiomeGenBase.forest);
		this.field_35143_f.add(BiomeGenBase.swampland);
		this.field_35143_f.add(BiomeGenBase.taiga);
	}

	public WorldChunkManager(World var1) {
		this();
		GenLayer[] var2 = GenLayer.func_35019_a(var1.getRandomSeed());
		this.field_34907_a = var2[0];
		this.field_34906_b = var2[1];
		this.field_34905_c = var2[2];
		this.field_35144_d = var2[3];
	}

	public List func_35137_a() {
		return this.field_35143_f;
	}

	public BiomeGenBase getBiomeGenAtChunkCoord(ChunkCoordIntPair var1) {
		return this.getBiomeGenAt(var1.chunkXPos << 4, var1.chunkZPos << 4);
	}

	public BiomeGenBase getBiomeGenAt(int var1, int var2) {
		return this.field_35145_e.func_35683_a(var1, var2);
	}

	public float[] func_4065_a(float[] var1, int var2, int var3, int var4, int var5) {
		IntCache.func_35550_a();
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new float[var4 * var5];
		}

		int[] var6 = this.field_35144_d.func_35018_a(var2, var3, var4, var5);

		for(int var7 = 0; var7 < var4 * var5; ++var7) {
			float var8 = (float)var6[var7] / 65536.0F;
			if(var8 > 1.0F) {
				var8 = 1.0F;
			}

			var1[var7] = var8;
		}

		return var1;
	}

	public float[] getTemperatures(float[] var1, int var2, int var3, int var4, int var5) {
		IntCache.func_35550_a();
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new float[var4 * var5];
		}

		int[] var6 = this.field_34905_c.func_35018_a(var2, var3, var4, var5);

		for(int var7 = 0; var7 < var4 * var5; ++var7) {
			float var8 = (float)var6[var7] / 65536.0F;
			if(var8 > 1.0F) {
				var8 = 1.0F;
			}

			var1[var7] = var8;
		}

		return var1;
	}

	public BiomeGenBase[] func_35142_b(BiomeGenBase[] var1, int var2, int var3, int var4, int var5) {
		IntCache.func_35550_a();
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new BiomeGenBase[var4 * var5];
		}

		int[] var6 = this.field_34907_a.func_35018_a(var2, var3, var4, var5);

		for(int var7 = 0; var7 < var4 * var5; ++var7) {
			var1[var7] = BiomeGenBase.field_35521_a[var6[var7]];
		}

		return var1;
	}

	public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] var1, int var2, int var3, int var4, int var5) {
		return this.func_35140_a(var1, var2, var3, var4, var5, true);
	}

	public BiomeGenBase[] func_35140_a(BiomeGenBase[] var1, int var2, int var3, int var4, int var5, boolean var6) {
		IntCache.func_35550_a();
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new BiomeGenBase[var4 * var5];
		}

		if(var6 && var4 == 16 && var5 == 16 && (var2 & 15) == 0 && (var3 & 15) == 0) {
			BiomeGenBase[] var9 = this.field_35145_e.func_35682_b(var2, var3);
			System.arraycopy(var9, 0, var1, 0, var4 * var5);
			return var1;
		} else {
			int[] var7 = this.field_34906_b.func_35018_a(var2, var3, var4, var5);

			for(int var8 = 0; var8 < var4 * var5; ++var8) {
				var1[var8] = BiomeGenBase.field_35521_a[var7[var8]];
			}

			return var1;
		}
	}

	public boolean func_35141_a(int var1, int var2, int var3, List var4) {
		int var5 = var1 - var3 >> 2;
		int var6 = var2 - var3 >> 2;
		int var7 = var1 + var3 >> 2;
		int var8 = var2 + var3 >> 2;
		int var9 = var7 - var5 + 1;
		int var10 = var8 - var6 + 1;
		int[] var11 = this.field_34907_a.func_35018_a(var5, var6, var9, var10);

		for(int var12 = 0; var12 < var9 * var10; ++var12) {
			BiomeGenBase var13 = BiomeGenBase.field_35521_a[var11[var12]];
			if(!var4.contains(var13)) {
				return false;
			}
		}

		return true;
	}

	public ChunkPosition func_35139_a(int var1, int var2, int var3, List var4, Random var5) {
		int var6 = var1 - var3 >> 2;
		int var7 = var2 - var3 >> 2;
		int var8 = var1 + var3 >> 2;
		int var9 = var2 + var3 >> 2;
		int var10 = var8 - var6 + 1;
		int var11 = var9 - var7 + 1;
		int[] var12 = this.field_34907_a.func_35018_a(var6, var7, var10, var11);
		ChunkPosition var13 = null;
		int var14 = 0;

		for(int var15 = 0; var15 < var12.length; ++var15) {
			int var16 = var6 + var15 % var10 << 2;
			int var17 = var7 + var15 / var10 << 2;
			BiomeGenBase var18 = BiomeGenBase.field_35521_a[var12[var15]];
			if(var4.contains(var18) && (var13 == null || var5.nextInt(var14 + 1) == 0)) {
				var13 = new ChunkPosition(var16, 0, var17);
				++var14;
			}
		}

		return var13;
	}

	public void func_35138_b() {
		this.field_35145_e.func_35681_a();
	}
}
