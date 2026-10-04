package net.minecraft.src;

import java.util.Random;

public class BiomeDecorator {
	private World field_35262_B;
	private Random field_35263_C;
	private int field_35258_D;
	private int field_35259_E;
	private BiomeGenBase field_35260_F;
	protected WorldGenerator field_35270_a = new WorldGenClay(4);
	protected WorldGenerator field_35268_b = new WorldGenSand(7, Block.sand.blockID);
	protected WorldGenerator field_35269_c = new WorldGenSand(6, Block.gravel.blockID);
	protected WorldGenerator field_35266_d = new WorldGenMinable(Block.dirt.blockID, 32);
	protected WorldGenerator field_35267_e = new WorldGenMinable(Block.gravel.blockID, 32);
	protected WorldGenerator field_35264_f = new WorldGenMinable(Block.oreCoal.blockID, 16);
	protected WorldGenerator field_35265_g = new WorldGenMinable(Block.oreIron.blockID, 8);
	protected WorldGenerator field_35277_h = new WorldGenMinable(Block.oreGold.blockID, 8);
	protected WorldGenerator field_35278_i = new WorldGenMinable(Block.oreRedstone.blockID, 7);
	protected WorldGenerator field_35275_j = new WorldGenMinable(Block.oreDiamond.blockID, 7);
	protected WorldGenerator field_35276_k = new WorldGenMinable(Block.oreLapis.blockID, 6);
	protected WorldGenerator field_35273_l = new WorldGenFlowers(Block.plantYellow.blockID);
	protected WorldGenerator field_35274_m = new WorldGenFlowers(Block.plantYellow.blockID);
	protected WorldGenerator field_35271_n = new WorldGenFlowers(Block.mushroomBrown.blockID);
	protected WorldGenerator field_35272_o = new WorldGenFlowers(Block.mushroomRed.blockID);
	protected WorldGenerator field_35286_p = new WorldGenReed();
	protected WorldGenerator field_35285_q = new WorldGenCactus();
	protected int field_35284_r = 0;
	protected int field_35283_s = 2;
	protected int field_35282_t = 1;
	protected int field_35281_u = 0;
	protected int field_35280_v = 0;
	protected int field_35279_w = 0;
	protected int field_35289_x = 0;
	protected int field_35288_y = 1;
	protected int field_35287_z = 3;
	protected int field_35261_A = 1;

	public BiomeDecorator(BiomeGenBase var1) {
		this.field_35260_F = var1;
	}

	public void func_35255_a(World var1, Random var2, int var3, int var4) {
		if(this.field_35262_B != null) {
			throw new RuntimeException("Already decorating!!");
		} else {
			this.field_35262_B = var1;
			this.field_35263_C = var2;
			this.field_35258_D = var3;
			this.field_35259_E = var4;
			this.func_35256_b();
			this.field_35262_B = null;
			this.field_35263_C = null;
		}
	}

	private void func_35256_b() {
		this.func_35253_a();

		int var1;
		int var2;
		int var3;
		for(var1 = 0; var1 < this.field_35287_z; ++var1) {
			var2 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var3 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			this.field_35268_b.generate(this.field_35262_B, this.field_35263_C, var2, this.field_35262_B.findTopSolidBlock(var2, var3), var3);
		}

		for(var1 = 0; var1 < this.field_35261_A; ++var1) {
			var2 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var3 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			this.field_35270_a.generate(this.field_35262_B, this.field_35263_C, var2, this.field_35262_B.findTopSolidBlock(var2, var3), var3);
		}

		for(var1 = 0; var1 < this.field_35288_y; ++var1) {
			var2 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var3 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			this.field_35268_b.generate(this.field_35262_B, this.field_35263_C, var2, this.field_35262_B.findTopSolidBlock(var2, var3), var3);
		}

		var1 = this.field_35284_r;
		if(this.field_35263_C.nextInt(10) == 0) {
			++var1;
		}

		int var4;
		for(var2 = 0; var2 < var1; ++var2) {
			var3 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var4 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			WorldGenerator var5 = this.field_35260_F.getRandomWorldGenForTrees(this.field_35263_C);
			var5.func_420_a(1.0D, 1.0D, 1.0D);
			var5.generate(this.field_35262_B, this.field_35263_C, var3, this.field_35262_B.getHeightValue(var3, var4), var4);
		}

		Random var10000;
		int var8;
		for(var2 = 0; var2 < this.field_35283_s; ++var2) {
			var3 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var10000 = this.field_35263_C;
			this.field_35262_B.getClass();
			var4 = var10000.nextInt(128);
			var8 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			this.field_35273_l.generate(this.field_35262_B, this.field_35263_C, var3, var4, var8);
			if(this.field_35263_C.nextInt(4) == 0) {
				var3 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
				var10000 = this.field_35263_C;
				this.field_35262_B.getClass();
				var4 = var10000.nextInt(128);
				var8 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
				this.field_35274_m.generate(this.field_35262_B, this.field_35263_C, var3, var4, var8);
			}
		}

		for(var2 = 0; var2 < this.field_35282_t; ++var2) {
			byte var7 = 1;
			var4 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var10000 = this.field_35263_C;
			this.field_35262_B.getClass();
			var8 = var10000.nextInt(128);
			int var6 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			(new WorldGenTallGrass(Block.tallGrass.blockID, var7)).generate(this.field_35262_B, this.field_35263_C, var4, var8, var6);
		}

		for(var2 = 0; var2 < this.field_35281_u; ++var2) {
			var3 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var10000 = this.field_35263_C;
			this.field_35262_B.getClass();
			var4 = var10000.nextInt(128);
			var8 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			(new WorldGenDeadBush(Block.deadBush.blockID)).generate(this.field_35262_B, this.field_35263_C, var3, var4, var8);
		}

		for(var2 = 0; var2 < this.field_35280_v; ++var2) {
			if(this.field_35263_C.nextInt(4) == 0) {
				var3 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
				var4 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
				var8 = this.field_35262_B.getHeightValue(var3, var4);
				this.field_35271_n.generate(this.field_35262_B, this.field_35263_C, var3, var8, var4);
			}

			if(this.field_35263_C.nextInt(8) == 0) {
				var3 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
				var4 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
				var10000 = this.field_35263_C;
				this.field_35262_B.getClass();
				var8 = var10000.nextInt(128);
				this.field_35272_o.generate(this.field_35262_B, this.field_35263_C, var3, var8, var4);
			}
		}

		if(this.field_35263_C.nextInt(4) == 0) {
			var2 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var10000 = this.field_35263_C;
			this.field_35262_B.getClass();
			var3 = var10000.nextInt(128);
			var4 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			this.field_35271_n.generate(this.field_35262_B, this.field_35263_C, var2, var3, var4);
		}

		if(this.field_35263_C.nextInt(8) == 0) {
			var2 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var10000 = this.field_35263_C;
			this.field_35262_B.getClass();
			var3 = var10000.nextInt(128);
			var4 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			this.field_35272_o.generate(this.field_35262_B, this.field_35263_C, var2, var3, var4);
		}

		for(var2 = 0; var2 < this.field_35279_w; ++var2) {
			var3 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var4 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			var10000 = this.field_35263_C;
			this.field_35262_B.getClass();
			var8 = var10000.nextInt(128);
			this.field_35286_p.generate(this.field_35262_B, this.field_35263_C, var3, var8, var4);
		}

		for(var2 = 0; var2 < 10; ++var2) {
			var3 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var10000 = this.field_35263_C;
			this.field_35262_B.getClass();
			var4 = var10000.nextInt(128);
			var8 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			this.field_35286_p.generate(this.field_35262_B, this.field_35263_C, var3, var4, var8);
		}

		if(this.field_35263_C.nextInt(32) == 0) {
			var2 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var10000 = this.field_35263_C;
			this.field_35262_B.getClass();
			var3 = var10000.nextInt(128);
			var4 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			(new WorldGenPumpkin()).generate(this.field_35262_B, this.field_35263_C, var2, var3, var4);
		}

		for(var2 = 0; var2 < this.field_35289_x; ++var2) {
			var3 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var10000 = this.field_35263_C;
			this.field_35262_B.getClass();
			var4 = var10000.nextInt(128);
			var8 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			this.field_35285_q.generate(this.field_35262_B, this.field_35263_C, var3, var4, var8);
		}

		Random var10001;
		for(var2 = 0; var2 < 50; ++var2) {
			var3 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var10000 = this.field_35263_C;
			var10001 = this.field_35263_C;
			this.field_35262_B.getClass();
			var4 = var10000.nextInt(var10001.nextInt(128 - 8) + 8);
			var8 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			(new WorldGenLiquids(Block.waterMoving.blockID)).generate(this.field_35262_B, this.field_35263_C, var3, var4, var8);
		}

		for(var2 = 0; var2 < 20; ++var2) {
			var3 = this.field_35258_D + this.field_35263_C.nextInt(16) + 8;
			var10000 = this.field_35263_C;
			var10001 = this.field_35263_C;
			Random var10002 = this.field_35263_C;
			this.field_35262_B.getClass();
			var4 = var10000.nextInt(var10001.nextInt(var10002.nextInt(128 - 16) + 8) + 8);
			var8 = this.field_35259_E + this.field_35263_C.nextInt(16) + 8;
			(new WorldGenLiquids(Block.lavaMoving.blockID)).generate(this.field_35262_B, this.field_35263_C, var3, var4, var8);
		}

	}

	protected void func_35257_a(int var1, WorldGenerator var2, int var3, int var4) {
		for(int var5 = 0; var5 < var1; ++var5) {
			int var6 = this.field_35258_D + this.field_35263_C.nextInt(16);
			int var7 = this.field_35263_C.nextInt(var4 - var3) + var3;
			int var8 = this.field_35259_E + this.field_35263_C.nextInt(16);
			var2.generate(this.field_35262_B, this.field_35263_C, var6, var7, var8);
		}

	}

	protected void func_35254_b(int var1, WorldGenerator var2, int var3, int var4) {
		for(int var5 = 0; var5 < var1; ++var5) {
			int var6 = this.field_35258_D + this.field_35263_C.nextInt(16);
			int var7 = this.field_35263_C.nextInt(var4) + this.field_35263_C.nextInt(var4) + (var3 - var4);
			int var8 = this.field_35259_E + this.field_35263_C.nextInt(16);
			var2.generate(this.field_35262_B, this.field_35263_C, var6, var7, var8);
		}

	}

	protected void func_35253_a() {
		WorldGenerator var10002 = this.field_35266_d;
		this.field_35262_B.getClass();
		this.func_35257_a(20, var10002, 0, 128);
		var10002 = this.field_35267_e;
		this.field_35262_B.getClass();
		this.func_35257_a(10, var10002, 0, 128);
		var10002 = this.field_35264_f;
		this.field_35262_B.getClass();
		this.func_35257_a(20, var10002, 0, 128);
		var10002 = this.field_35265_g;
		this.field_35262_B.getClass();
		this.func_35257_a(20, var10002, 0, 128 / 2);
		var10002 = this.field_35277_h;
		this.field_35262_B.getClass();
		this.func_35257_a(2, var10002, 0, 128 / 4);
		var10002 = this.field_35278_i;
		this.field_35262_B.getClass();
		this.func_35257_a(8, var10002, 0, 128 / 8);
		var10002 = this.field_35275_j;
		this.field_35262_B.getClass();
		this.func_35257_a(1, var10002, 0, 128 / 8);
		var10002 = this.field_35276_k;
		this.field_35262_B.getClass();
		int var10003 = 128 / 8;
		this.field_35262_B.getClass();
		this.func_35254_b(1, var10002, var10003, 128 / 8);
	}
}
