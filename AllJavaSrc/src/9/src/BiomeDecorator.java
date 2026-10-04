package net.minecraft.src;

import java.util.Random;

public class BiomeDecorator {
	private World field_35889_B;
	private Random field_35890_C;
	private int field_35885_D;
	private int field_35886_E;
	private BiomeGenBase field_35887_F;
	protected WorldGenerator field_35897_a = new WorldGenClay(4);
	protected WorldGenerator field_35895_b = new WorldGenSand(7, Block.sand.blockID);
	protected WorldGenerator field_35896_c = new WorldGenSand(6, Block.gravel.blockID);
	protected WorldGenerator field_35893_d = new WorldGenMinable(Block.dirt.blockID, 32);
	protected WorldGenerator field_35894_e = new WorldGenMinable(Block.gravel.blockID, 32);
	protected WorldGenerator field_35891_f = new WorldGenMinable(Block.oreCoal.blockID, 16);
	protected WorldGenerator field_35892_g = new WorldGenMinable(Block.oreIron.blockID, 8);
	protected WorldGenerator field_35904_h = new WorldGenMinable(Block.oreGold.blockID, 8);
	protected WorldGenerator field_35905_i = new WorldGenMinable(Block.oreRedstone.blockID, 7);
	protected WorldGenerator field_35902_j = new WorldGenMinable(Block.oreDiamond.blockID, 7);
	protected WorldGenerator field_35903_k = new WorldGenMinable(Block.oreLapis.blockID, 6);
	protected WorldGenerator field_35900_l = new WorldGenFlowers(Block.plantYellow.blockID);
	protected WorldGenerator field_35901_m = new WorldGenFlowers(Block.plantYellow.blockID);
	protected WorldGenerator field_35898_n = new WorldGenFlowers(Block.mushroomBrown.blockID);
	protected WorldGenerator field_35899_o = new WorldGenFlowers(Block.mushroomRed.blockID);
	protected WorldGenerator field_35913_p = new WorldGenReed();
	protected WorldGenerator field_35912_q = new WorldGenCactus();
	protected int field_35911_r = 0;
	protected int field_35910_s = 2;
	protected int field_35909_t = 1;
	protected int field_35908_u = 0;
	protected int field_35907_v = 0;
	protected int field_35906_w = 0;
	protected int field_35916_x = 0;
	protected int field_35915_y = 1;
	protected int field_35914_z = 3;
	protected int field_35888_A = 1;

	public BiomeDecorator(BiomeGenBase var1) {
		this.field_35887_F = var1;
	}

	public void func_35881_a(World var1, Random var2, int var3, int var4) {
		if(this.field_35889_B != null) {
			throw new RuntimeException("Already decorating!!");
		} else {
			this.field_35889_B = var1;
			this.field_35890_C = var2;
			this.field_35885_D = var3;
			this.field_35886_E = var4;
			this.func_35882_b();
			this.field_35889_B = null;
			this.field_35890_C = null;
		}
	}

	private void func_35882_b() {
		this.func_35880_a();

		int var1;
		int var2;
		int var3;
		for(var1 = 0; var1 < this.field_35914_z; ++var1) {
			var2 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var3 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			this.field_35895_b.generate(this.field_35889_B, this.field_35890_C, var2, this.field_35889_B.getTopSolidOrLiquidBlock(var2, var3), var3);
		}

		for(var1 = 0; var1 < this.field_35888_A; ++var1) {
			var2 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var3 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			this.field_35897_a.generate(this.field_35889_B, this.field_35890_C, var2, this.field_35889_B.getTopSolidOrLiquidBlock(var2, var3), var3);
		}

		for(var1 = 0; var1 < this.field_35915_y; ++var1) {
			var2 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var3 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			this.field_35895_b.generate(this.field_35889_B, this.field_35890_C, var2, this.field_35889_B.getTopSolidOrLiquidBlock(var2, var3), var3);
		}

		var1 = this.field_35911_r;
		if(this.field_35890_C.nextInt(10) == 0) {
			++var1;
		}

		int var4;
		for(var2 = 0; var2 < var1; ++var2) {
			var3 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var4 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			WorldGenerator var5 = this.field_35887_F.getRandomWorldGenForTrees(this.field_35890_C);
			var5.func_517_a(1.0D, 1.0D, 1.0D);
			var5.generate(this.field_35889_B, this.field_35890_C, var3, this.field_35889_B.getHeightValue(var3, var4), var4);
		}

		Random var10000;
		int var8;
		for(var2 = 0; var2 < this.field_35910_s; ++var2) {
			var3 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var10000 = this.field_35890_C;
			this.field_35889_B.getClass();
			var4 = var10000.nextInt(128);
			var8 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			this.field_35900_l.generate(this.field_35889_B, this.field_35890_C, var3, var4, var8);
			if(this.field_35890_C.nextInt(4) == 0) {
				var3 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
				var10000 = this.field_35890_C;
				this.field_35889_B.getClass();
				var4 = var10000.nextInt(128);
				var8 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
				this.field_35901_m.generate(this.field_35889_B, this.field_35890_C, var3, var4, var8);
			}
		}

		for(var2 = 0; var2 < this.field_35909_t; ++var2) {
			byte var7 = 1;
			var4 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var10000 = this.field_35890_C;
			this.field_35889_B.getClass();
			var8 = var10000.nextInt(128);
			int var6 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			(new WorldGenTallGrass(Block.tallGrass.blockID, var7)).generate(this.field_35889_B, this.field_35890_C, var4, var8, var6);
		}

		for(var2 = 0; var2 < this.field_35908_u; ++var2) {
			var3 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var10000 = this.field_35890_C;
			this.field_35889_B.getClass();
			var4 = var10000.nextInt(128);
			var8 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			(new WorldGenDeadBush(Block.deadBush.blockID)).generate(this.field_35889_B, this.field_35890_C, var3, var4, var8);
		}

		for(var2 = 0; var2 < this.field_35907_v; ++var2) {
			if(this.field_35890_C.nextInt(4) == 0) {
				var3 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
				var4 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
				var8 = this.field_35889_B.getHeightValue(var3, var4);
				this.field_35898_n.generate(this.field_35889_B, this.field_35890_C, var3, var8, var4);
			}

			if(this.field_35890_C.nextInt(8) == 0) {
				var3 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
				var4 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
				var10000 = this.field_35890_C;
				this.field_35889_B.getClass();
				var8 = var10000.nextInt(128);
				this.field_35899_o.generate(this.field_35889_B, this.field_35890_C, var3, var8, var4);
			}
		}

		if(this.field_35890_C.nextInt(4) == 0) {
			var2 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var10000 = this.field_35890_C;
			this.field_35889_B.getClass();
			var3 = var10000.nextInt(128);
			var4 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			this.field_35898_n.generate(this.field_35889_B, this.field_35890_C, var2, var3, var4);
		}

		if(this.field_35890_C.nextInt(8) == 0) {
			var2 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var10000 = this.field_35890_C;
			this.field_35889_B.getClass();
			var3 = var10000.nextInt(128);
			var4 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			this.field_35899_o.generate(this.field_35889_B, this.field_35890_C, var2, var3, var4);
		}

		for(var2 = 0; var2 < this.field_35906_w; ++var2) {
			var3 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var4 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			var10000 = this.field_35890_C;
			this.field_35889_B.getClass();
			var8 = var10000.nextInt(128);
			this.field_35913_p.generate(this.field_35889_B, this.field_35890_C, var3, var8, var4);
		}

		for(var2 = 0; var2 < 10; ++var2) {
			var3 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var10000 = this.field_35890_C;
			this.field_35889_B.getClass();
			var4 = var10000.nextInt(128);
			var8 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			this.field_35913_p.generate(this.field_35889_B, this.field_35890_C, var3, var4, var8);
		}

		if(this.field_35890_C.nextInt(32) == 0) {
			var2 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var10000 = this.field_35890_C;
			this.field_35889_B.getClass();
			var3 = var10000.nextInt(128);
			var4 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			(new WorldGenPumpkin()).generate(this.field_35889_B, this.field_35890_C, var2, var3, var4);
		}

		for(var2 = 0; var2 < this.field_35916_x; ++var2) {
			var3 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var10000 = this.field_35890_C;
			this.field_35889_B.getClass();
			var4 = var10000.nextInt(128);
			var8 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			this.field_35912_q.generate(this.field_35889_B, this.field_35890_C, var3, var4, var8);
		}

		Random var10001;
		for(var2 = 0; var2 < 50; ++var2) {
			var3 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var10000 = this.field_35890_C;
			var10001 = this.field_35890_C;
			this.field_35889_B.getClass();
			var4 = var10000.nextInt(var10001.nextInt(128 - 8) + 8);
			var8 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			(new WorldGenLiquids(Block.waterMoving.blockID)).generate(this.field_35889_B, this.field_35890_C, var3, var4, var8);
		}

		for(var2 = 0; var2 < 20; ++var2) {
			var3 = this.field_35885_D + this.field_35890_C.nextInt(16) + 8;
			var10000 = this.field_35890_C;
			var10001 = this.field_35890_C;
			Random var10002 = this.field_35890_C;
			this.field_35889_B.getClass();
			var4 = var10000.nextInt(var10001.nextInt(var10002.nextInt(128 - 16) + 8) + 8);
			var8 = this.field_35886_E + this.field_35890_C.nextInt(16) + 8;
			(new WorldGenLiquids(Block.lavaMoving.blockID)).generate(this.field_35889_B, this.field_35890_C, var3, var4, var8);
		}

	}

	protected void func_35884_a(int var1, WorldGenerator var2, int var3, int var4) {
		for(int var5 = 0; var5 < var1; ++var5) {
			int var6 = this.field_35885_D + this.field_35890_C.nextInt(16);
			int var7 = this.field_35890_C.nextInt(var4 - var3) + var3;
			int var8 = this.field_35886_E + this.field_35890_C.nextInt(16);
			var2.generate(this.field_35889_B, this.field_35890_C, var6, var7, var8);
		}

	}

	protected void func_35883_b(int var1, WorldGenerator var2, int var3, int var4) {
		for(int var5 = 0; var5 < var1; ++var5) {
			int var6 = this.field_35885_D + this.field_35890_C.nextInt(16);
			int var7 = this.field_35890_C.nextInt(var4) + this.field_35890_C.nextInt(var4) + (var3 - var4);
			int var8 = this.field_35886_E + this.field_35890_C.nextInt(16);
			var2.generate(this.field_35889_B, this.field_35890_C, var6, var7, var8);
		}

	}

	protected void func_35880_a() {
		WorldGenerator var10002 = this.field_35893_d;
		this.field_35889_B.getClass();
		this.func_35884_a(20, var10002, 0, 128);
		var10002 = this.field_35894_e;
		this.field_35889_B.getClass();
		this.func_35884_a(10, var10002, 0, 128);
		var10002 = this.field_35891_f;
		this.field_35889_B.getClass();
		this.func_35884_a(20, var10002, 0, 128);
		var10002 = this.field_35892_g;
		this.field_35889_B.getClass();
		this.func_35884_a(20, var10002, 0, 128 / 2);
		var10002 = this.field_35904_h;
		this.field_35889_B.getClass();
		this.func_35884_a(2, var10002, 0, 128 / 4);
		var10002 = this.field_35905_i;
		this.field_35889_B.getClass();
		this.func_35884_a(8, var10002, 0, 128 / 8);
		var10002 = this.field_35902_j;
		this.field_35889_B.getClass();
		this.func_35884_a(1, var10002, 0, 128 / 8);
		var10002 = this.field_35903_k;
		this.field_35889_B.getClass();
		int var10003 = 128 / 8;
		this.field_35889_B.getClass();
		this.func_35883_b(1, var10002, var10003, 128 / 8);
	}
}
