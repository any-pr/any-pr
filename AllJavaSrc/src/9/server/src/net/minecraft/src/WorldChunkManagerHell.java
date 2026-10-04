package net.minecraft.src;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class WorldChunkManagerHell extends WorldChunkManager {
	private BiomeGenBase field_4262_e;
	private float field_4261_f;
	private float field_4260_g;

	public WorldChunkManagerHell(BiomeGenBase var1, float var2, float var3) {
		this.field_4262_e = var1;
		this.field_4261_f = var2;
		this.field_4260_g = var3;
	}

	public BiomeGenBase getBiomeGenAtChunkCoord(ChunkCoordIntPair var1) {
		return this.field_4262_e;
	}

	public BiomeGenBase getBiomeGenAt(int var1, int var2) {
		return this.field_4262_e;
	}

	public float[] getTemperatures(float[] var1, int var2, int var3, int var4, int var5) {
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new float[var4 * var5];
		}

		Arrays.fill(var1, 0, var4 * var5, this.field_4261_f);
		return var1;
	}

	public float[] func_4065_a(float[] var1, int var2, int var3, int var4, int var5) {
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new float[var4 * var5];
		}

		Arrays.fill(var1, 0, var4 * var5, this.field_4260_g);
		return var1;
	}

	public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] var1, int var2, int var3, int var4, int var5) {
		if(var1 == null || var1.length < var4 * var5) {
			var1 = new BiomeGenBase[var4 * var5];
		}

		Arrays.fill(var1, 0, var4 * var5, this.field_4262_e);
		return var1;
	}

	public ChunkPosition func_35139_a(int var1, int var2, int var3, List var4, Random var5) {
		return var4.contains(this.field_4262_e) ? new ChunkPosition(var1 - var3 + var5.nextInt(var3 * 2 + 1), 0, var2 - var3 + var5.nextInt(var3 * 2 + 1)) : null;
	}

	public boolean func_35141_a(int var1, int var2, int var3, List var4) {
		return var4.contains(this.field_4262_e);
	}
}
