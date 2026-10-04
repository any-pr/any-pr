package net.minecraft.src;

import java.util.Random;

public class BiomeGenSwamp extends BiomeGenBase {
	protected BiomeGenSwamp(int var1) {
		super(var1);
		this.field_35523_u.field_35284_r = 2;
		this.field_35523_u.field_35283_s = -999;
		this.field_35523_u.field_35281_u = 1;
		this.field_35523_u.field_35280_v = 8;
		this.field_35523_u.field_35279_w = 10;
		this.field_35523_u.field_35261_A = 1;
	}

	public WorldGenerator getRandomWorldGenForTrees(Random var1) {
		return this.field_35517_C;
	}
}
