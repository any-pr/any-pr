package net.minecraft.src;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class WorldChunkManagerHell extends WorldChunkManager {
	private BiomeGenBase field_4201_e;
	private float field_4200_f;
	private float field_4199_g;

	public WorldChunkManagerHell(BiomeGenBase var1, float var2, float var3) {
		this.field_4201_e = var1;
		this.field_4200_f = var2;
		this.field_4199_g = var3;
	}

	public BiomeGenBase getBiomeGenAtChunkCoord(ChunkCoordIntPair var1) {
		return this.field_4201_e;
	}

	public BiomeGenBase getBiomeGenAt(int var1, int var2) {
		return this.field_4201_e;
	}

	public float func_35554_b(int var1, int var2) {
		return this.field_4200_f;
	}

	public BiomeGenBase[] func_4069_a(int var1, int var2, int var3, int var4) {
		this.field_4195_d = this.loadBlockGeneratorData(this.field_4195_d, var1, var2, var3, var4);
		return this.field_4195_d;
	}

	public float[] getTemperatures(float[] var1, int var2, int var3, int var4, int var5) {
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new float[var4 * var5];
		}

		Arrays.fill(var1, 0, var4 * var5, this.field_4200_f);
		return var1;
	}

	public float[] func_35560_b(float[] var1, int var2, int var3, int var4, int var5) {
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new float[var4 * var5];
		}

		Arrays.fill(var1, 0, var4 * var5, this.field_4199_g);
		return var1;
	}

	public float func_35558_c(int var1, int var2) {
		return this.field_4199_g;
	}

	public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] var1, int var2, int var3, int var4, int var5) {
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new BiomeGenBase[var4 * var5];
		}

		Arrays.fill(var1, 0, var4 * var5, this.field_4201_e);
		return var1;
	}

	public ChunkPosition func_35556_a(int var1, int var2, int var3, List var4, Random var5) {
		return var4.contains(this.field_4201_e) ? new ChunkPosition(var1 - var3 + var5.nextInt(var3 * 2 + 1), 0, var2 - var3 + var5.nextInt(var3 * 2 + 1)) : null;
	}

	public boolean func_35562_a(int var1, int var2, int var3, List var4) {
		return var4.contains(this.field_4201_e);
	}
}
