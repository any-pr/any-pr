package net.minecraft.src;

import java.util.Random;

public class BiomeGenSwamp extends BiomeGenBase {
	protected BiomeGenSwamp(int var1) {
		super(var1);
		this.field_35488_u.field_35911_r = 2;
		this.field_35488_u.field_35910_s = -999;
		this.field_35488_u.field_35908_u = 1;
		this.field_35488_u.field_35907_v = 8;
		this.field_35488_u.field_35906_w = 10;
		this.field_35488_u.field_35888_A = 1;
	}

	public WorldGenerator getRandomWorldGenForTrees(Random var1) {
		return this.field_35482_C;
	}
}
