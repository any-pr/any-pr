package net.minecraft.src;

import java.util.Random;

public class ChunkProviderSky implements IChunkProvider {
	private Random random;
	private NoiseGeneratorOctaves field_28086_k;
	private NoiseGeneratorOctaves field_28085_l;
	private NoiseGeneratorOctaves field_28084_m;
	private NoiseGeneratorOctaves field_28083_n;
	private NoiseGeneratorOctaves field_28082_o;
	public NoiseGeneratorOctaves field_28096_a;
	public NoiseGeneratorOctaves field_28095_b;
	public NoiseGeneratorOctaves field_28094_c;
	private World worldObj;
	private double[] field_28080_q;
	private double[] unusedSandNoise = new double[256];
	private double[] unusedGravelNoise = new double[256];
	private double[] stoneNoise = new double[256];
	private MapGenBase caveGen = new MapGenCaves();
	private BiomeGenBase[] field_28075_v;
	double[] field_28093_d;
	double[] field_28092_e;
	double[] field_28091_f;
	double[] field_28090_g;
	double[] field_28089_h;
	int[][] field_28088_i = new int[32][32];

	public ChunkProviderSky(World var1, long var2) {
		this.worldObj = var1;
		this.random = new Random(var2);
		this.field_28086_k = new NoiseGeneratorOctaves(this.random, 16);
		this.field_28085_l = new NoiseGeneratorOctaves(this.random, 16);
		this.field_28084_m = new NoiseGeneratorOctaves(this.random, 8);
		this.field_28083_n = new NoiseGeneratorOctaves(this.random, 4);
		this.field_28082_o = new NoiseGeneratorOctaves(this.random, 4);
		this.field_28096_a = new NoiseGeneratorOctaves(this.random, 10);
		this.field_28095_b = new NoiseGeneratorOctaves(this.random, 16);
		this.field_28094_c = new NoiseGeneratorOctaves(this.random, 8);
	}

	public void generateTerrain(int var1, int var2, byte[] var3, BiomeGenBase[] var4) {
		byte var5 = 2;
		int var6 = var5 + 1;
		this.worldObj.getClass();
		int var7 = 128 / 4 + 1;
		int var8 = var5 + 1;
		this.field_28080_q = this.func_28073_a(this.field_28080_q, var1 * var5, 0, var2 * var5, var6, var7, var8);

		for(int var9 = 0; var9 < var5; ++var9) {
			for(int var10 = 0; var10 < var5; ++var10) {
				int var11 = 0;

				while(true) {
					this.worldObj.getClass();
					if(var11 >= 128 / 4) {
						break;
					}

					double var12 = 0.25D;
					double var14 = this.field_28080_q[((var9 + 0) * var8 + var10 + 0) * var7 + var11 + 0];
					double var16 = this.field_28080_q[((var9 + 0) * var8 + var10 + 1) * var7 + var11 + 0];
					double var18 = this.field_28080_q[((var9 + 1) * var8 + var10 + 0) * var7 + var11 + 0];
					double var20 = this.field_28080_q[((var9 + 1) * var8 + var10 + 1) * var7 + var11 + 0];
					double var22 = (this.field_28080_q[((var9 + 0) * var8 + var10 + 0) * var7 + var11 + 1] - var14) * var12;
					double var24 = (this.field_28080_q[((var9 + 0) * var8 + var10 + 1) * var7 + var11 + 1] - var16) * var12;
					double var26 = (this.field_28080_q[((var9 + 1) * var8 + var10 + 0) * var7 + var11 + 1] - var18) * var12;
					double var28 = (this.field_28080_q[((var9 + 1) * var8 + var10 + 1) * var7 + var11 + 1] - var20) * var12;

					for(int var30 = 0; var30 < 4; ++var30) {
						double var31 = 0.125D;
						double var33 = var14;
						double var35 = var16;
						double var37 = (var18 - var14) * var31;
						double var39 = (var20 - var16) * var31;

						for(int var41 = 0; var41 < 8; ++var41) {
							int var10000 = var41 + var9 * 8;
							this.worldObj.getClass();
							var10000 <<= 11;
							int var10001 = 0 + var10 * 8;
							this.worldObj.getClass();
							int var42 = var10000 | var10001 << 7 | var11 * 4 + var30;
							this.worldObj.getClass();
							int var43 = 1 << 7;
							double var44 = 0.125D;
							double var46 = var33;
							double var48 = (var35 - var33) * var44;

							for(int var50 = 0; var50 < 8; ++var50) {
								int var51 = 0;
								if(var46 > 0.0D) {
									var51 = Block.stone.blockID;
								}

								var3[var42] = (byte)var51;
								var42 += var43;
								var46 += var48;
							}

							var33 += var37;
							var35 += var39;
						}

						var14 += var22;
						var16 += var24;
						var18 += var26;
						var20 += var28;
					}

					++var11;
				}
			}
		}

	}

	public void replaceBlocksForBiome(int var1, int var2, byte[] var3, BiomeGenBase[] var4) {
		double var5 = 1.0D / 32.0D;
		this.unusedSandNoise = this.field_28083_n.generateNoiseOctaves(this.unusedSandNoise, var1 * 16, var2 * 16, 0, 16, 16, 1, var5, var5, 1.0D);
		this.unusedGravelNoise = this.field_28083_n.generateNoiseOctaves(this.unusedGravelNoise, var1 * 16, 109, var2 * 16, 16, 1, 16, var5, 1.0D, var5);
		this.stoneNoise = this.field_28082_o.generateNoiseOctaves(this.stoneNoise, var1 * 16, var2 * 16, 0, 16, 16, 1, var5 * 2.0D, var5 * 2.0D, var5 * 2.0D);

		for(int var7 = 0; var7 < 16; ++var7) {
			for(int var8 = 0; var8 < 16; ++var8) {
				BiomeGenBase var9 = var4[var7 + var8 * 16];
				int var10 = (int)(this.stoneNoise[var7 + var8 * 16] / 3.0D + 3.0D + this.random.nextDouble() * 0.25D);
				int var11 = -1;
				byte var12 = var9.topBlock;
				byte var13 = var9.fillerBlock;
				this.worldObj.getClass();

				for(int var14 = 127; var14 >= 0; --var14) {
					int var10000 = var8 * 16 + var7;
					this.worldObj.getClass();
					int var15 = var10000 * 128 + var14;
					byte var16 = var3[var15];
					if(var16 == 0) {
						var11 = -1;
					} else if(var16 == Block.stone.blockID) {
						if(var11 == -1) {
							if(var10 <= 0) {
								var12 = 0;
								var13 = (byte)Block.stone.blockID;
							}

							var11 = var10;
							if(var14 >= 0) {
								var3[var15] = var12;
							} else {
								var3[var15] = var13;
							}
						} else if(var11 > 0) {
							--var11;
							var3[var15] = var13;
							if(var11 == 0 && var13 == Block.sand.blockID) {
								var11 = this.random.nextInt(4);
								var13 = (byte)Block.sandStone.blockID;
							}
						}
					}
				}
			}
		}

	}

	public Chunk loadChunk(int var1, int var2) {
		return this.provideChunk(var1, var2);
	}

	public Chunk provideChunk(int var1, int var2) {
		this.random.setSeed((long)var1 * 341873128712L + (long)var2 * 132897987541L);
		this.worldObj.getClass();
		byte[] var3 = new byte[16 * 128 * 16];
		Chunk var4 = new Chunk(this.worldObj, var3, var1, var2);
		this.field_28075_v = this.worldObj.getWorldChunkManager().loadBlockGeneratorData(this.field_28075_v, var1 * 16, var2 * 16, 16, 16);
		this.generateTerrain(var1, var2, var3, this.field_28075_v);
		this.replaceBlocksForBiome(var1, var2, var3, this.field_28075_v);
		this.caveGen.generate(this, this.worldObj, var1, var2, var3);
		var4.generateSkylightMap();
		return var4;
	}

	private double[] func_28073_a(double[] var1, int var2, int var3, int var4, int var5, int var6, int var7) {
		if(var1 == null) {
			var1 = new double[var5 * var6 * var7];
		}

		double var8 = 684.412D;
		double var10 = 684.412D;
		this.field_28090_g = this.field_28096_a.func_4109_a(this.field_28090_g, var2, var4, var5, var7, 1.121D, 1.121D, 0.5D);
		this.field_28089_h = this.field_28095_b.func_4109_a(this.field_28089_h, var2, var4, var5, var7, 200.0D, 200.0D, 0.5D);
		var8 *= 2.0D;
		this.field_28093_d = this.field_28084_m.generateNoiseOctaves(this.field_28093_d, var2, var3, var4, var5, var6, var7, var8 / 80.0D, var10 / 160.0D, var8 / 80.0D);
		this.field_28092_e = this.field_28086_k.generateNoiseOctaves(this.field_28092_e, var2, var3, var4, var5, var6, var7, var8, var10, var8);
		this.field_28091_f = this.field_28085_l.generateNoiseOctaves(this.field_28091_f, var2, var3, var4, var5, var6, var7, var8, var10, var8);
		int var12 = 0;
		int var13 = 0;

		for(int var14 = 0; var14 < var5; ++var14) {
			for(int var15 = 0; var15 < var7; ++var15) {
				double var16 = (this.field_28090_g[var13] + 256.0D) / 512.0D;
				if(var16 > 1.0D) {
					var16 = 1.0D;
				}

				double var18 = this.field_28089_h[var13] / 8000.0D;
				if(var18 < 0.0D) {
					var18 = -var18 * 0.3D;
				}

				var18 = var18 * 3.0D - 2.0D;
				if(var18 > 1.0D) {
					var18 = 1.0D;
				}

				var18 /= 8.0D;
				var18 = 0.0D;
				if(var16 < 0.0D) {
					var16 = 0.0D;
				}

				var16 += 0.5D;
				var18 = var18 * (double)var6 / 16.0D;
				++var13;
				double var20 = (double)var6 / 2.0D;

				for(int var22 = 0; var22 < var6; ++var22) {
					double var23 = 0.0D;
					double var25 = ((double)var22 - var20) * 8.0D / var16;
					if(var25 < 0.0D) {
						var25 *= -1.0D;
					}

					double var27 = this.field_28092_e[var12] / 512.0D;
					double var29 = this.field_28091_f[var12] / 512.0D;
					double var31 = (this.field_28093_d[var12] / 10.0D + 1.0D) / 2.0D;
					if(var31 < 0.0D) {
						var23 = var27;
					} else if(var31 > 1.0D) {
						var23 = var29;
					} else {
						var23 = var27 + (var29 - var27) * var31;
					}

					var23 -= 8.0D;
					byte var33 = 32;
					double var34;
					if(var22 > var6 - var33) {
						var34 = (double)((float)(var22 - (var6 - var33)) / ((float)var33 - 1.0F));
						var23 = var23 * (1.0D - var34) + -30.0D * var34;
					}

					var33 = 8;
					if(var22 < var33) {
						var34 = (double)((float)(var33 - var22) / ((float)var33 - 1.0F));
						var23 = var23 * (1.0D - var34) + -30.0D * var34;
					}

					var1[var12] = var23;
					++var12;
				}
			}
		}

		return var1;
	}

	public boolean chunkExists(int var1, int var2) {
		return true;
	}

	public void populate(IChunkProvider var1, int var2, int var3) {
		BlockSand.fallInstantly = true;
		int var4 = var2 * 16;
		int var5 = var3 * 16;
		BiomeGenBase var6 = this.worldObj.getWorldChunkManager().getBiomeGenAt(var4 + 16, var5 + 16);
		this.random.setSeed(this.worldObj.getRandomSeed());
		long var7 = this.random.nextLong() / 2L * 2L + 1L;
		long var9 = this.random.nextLong() / 2L * 2L + 1L;
		this.random.setSeed((long)var2 * var7 + (long)var3 * var9 ^ this.worldObj.getRandomSeed());
		double var11 = 0.25D;
		int var13;
		int var14;
		int var15;
		Random var10000;
		if(this.random.nextInt(4) == 0) {
			var13 = var4 + this.random.nextInt(16) + 8;
			var10000 = this.random;
			this.worldObj.getClass();
			var14 = var10000.nextInt(128);
			var15 = var5 + this.random.nextInt(16) + 8;
			(new WorldGenLakes(Block.waterStill.blockID)).generate(this.worldObj, this.random, var13, var14, var15);
		}

		Random var10001;
		if(this.random.nextInt(8) == 0) {
			var13 = var4 + this.random.nextInt(16) + 8;
			var10000 = this.random;
			var10001 = this.random;
			this.worldObj.getClass();
			var14 = var10000.nextInt(var10001.nextInt(128 - 8) + 8);
			var15 = var5 + this.random.nextInt(16) + 8;
			if(var14 < 64 || this.random.nextInt(10) == 0) {
				(new WorldGenLakes(Block.lavaStill.blockID)).generate(this.worldObj, this.random, var13, var14, var15);
			}
		}

		int var16;
		for(var13 = 0; var13 < 8; ++var13) {
			var14 = var4 + this.random.nextInt(16) + 8;
			var10000 = this.random;
			this.worldObj.getClass();
			var15 = var10000.nextInt(128);
			var16 = var5 + this.random.nextInt(16) + 8;
			(new WorldGenDungeons()).generate(this.worldObj, this.random, var14, var15, var16);
		}

		for(var13 = 0; var13 < 10; ++var13) {
			var14 = var4 + this.random.nextInt(16);
			var10000 = this.random;
			this.worldObj.getClass();
			var15 = var10000.nextInt(128);
			var16 = var5 + this.random.nextInt(16);
			(new WorldGenClay(32)).generate(this.worldObj, this.random, var14, var15, var16);
		}

		for(var13 = 0; var13 < 20; ++var13) {
			var14 = var4 + this.random.nextInt(16);
			var10000 = this.random;
			this.worldObj.getClass();
			var15 = var10000.nextInt(128);
			var16 = var5 + this.random.nextInt(16);
			(new WorldGenMinable(Block.dirt.blockID, 32)).generate(this.worldObj, this.random, var14, var15, var16);
		}

		for(var13 = 0; var13 < 10; ++var13) {
			var14 = var4 + this.random.nextInt(16);
			var10000 = this.random;
			this.worldObj.getClass();
			var15 = var10000.nextInt(128);
			var16 = var5 + this.random.nextInt(16);
			(new WorldGenMinable(Block.gravel.blockID, 32)).generate(this.worldObj, this.random, var14, var15, var16);
		}

		for(var13 = 0; var13 < 20; ++var13) {
			var14 = var4 + this.random.nextInt(16);
			var10000 = this.random;
			this.worldObj.getClass();
			var15 = var10000.nextInt(128);
			var16 = var5 + this.random.nextInt(16);
			(new WorldGenMinable(Block.oreCoal.blockID, 16)).generate(this.worldObj, this.random, var14, var15, var16);
		}

		for(var13 = 0; var13 < 20; ++var13) {
			var14 = var4 + this.random.nextInt(16);
			var10000 = this.random;
			this.worldObj.getClass();
			var15 = var10000.nextInt(128 / 2);
			var16 = var5 + this.random.nextInt(16);
			(new WorldGenMinable(Block.oreIron.blockID, 8)).generate(this.worldObj, this.random, var14, var15, var16);
		}

		for(var13 = 0; var13 < 2; ++var13) {
			var14 = var4 + this.random.nextInt(16);
			var10000 = this.random;
			this.worldObj.getClass();
			var15 = var10000.nextInt(128 / 4);
			var16 = var5 + this.random.nextInt(16);
			(new WorldGenMinable(Block.oreGold.blockID, 8)).generate(this.worldObj, this.random, var14, var15, var16);
		}

		for(var13 = 0; var13 < 8; ++var13) {
			var14 = var4 + this.random.nextInt(16);
			var10000 = this.random;
			this.worldObj.getClass();
			var15 = var10000.nextInt(128 / 8);
			var16 = var5 + this.random.nextInt(16);
			(new WorldGenMinable(Block.oreRedstone.blockID, 7)).generate(this.worldObj, this.random, var14, var15, var16);
		}

		for(var13 = 0; var13 < 1; ++var13) {
			var14 = var4 + this.random.nextInt(16);
			var10000 = this.random;
			this.worldObj.getClass();
			var15 = var10000.nextInt(128 / 8);
			var16 = var5 + this.random.nextInt(16);
			(new WorldGenMinable(Block.oreDiamond.blockID, 7)).generate(this.worldObj, this.random, var14, var15, var16);
		}

		for(var13 = 0; var13 < 1; ++var13) {
			var14 = var4 + this.random.nextInt(16);
			var10000 = this.random;
			this.worldObj.getClass();
			int var21 = var10000.nextInt(128 / 8);
			var10001 = this.random;
			this.worldObj.getClass();
			var15 = var21 + var10001.nextInt(128 / 8);
			var16 = var5 + this.random.nextInt(16);
			(new WorldGenMinable(Block.oreLapis.blockID, 6)).generate(this.worldObj, this.random, var14, var15, var16);
		}

		var11 = 0.5D;
		var13 = (int)((this.field_28094_c.func_806_a((double)var4 * var11, (double)var5 * var11) / 8.0D + this.random.nextDouble() * 4.0D + 4.0D) / 3.0D);
		var14 = 0;
		if(this.random.nextInt(10) == 0) {
			++var14;
		}

		if(var6 == BiomeGenBase.forest) {
			var14 += var13 + 5;
		}

		if(var6 == BiomeGenBase.desert) {
			var14 -= 20;
		}

		if(var6 == BiomeGenBase.field_35485_c) {
			var14 -= 20;
		}

		int var17;
		for(var15 = 0; var15 < var14; ++var15) {
			var16 = var4 + this.random.nextInt(16) + 8;
			var17 = var5 + this.random.nextInt(16) + 8;
			WorldGenerator var18 = var6.getRandomWorldGenForTrees(this.random);
			var18.func_517_a(1.0D, 1.0D, 1.0D);
			var18.generate(this.worldObj, this.random, var16, this.worldObj.getHeightValue(var16, var17), var17);
		}

		int var20;
		for(var15 = 0; var15 < 2; ++var15) {
			var16 = var4 + this.random.nextInt(16) + 8;
			var10000 = this.random;
			this.worldObj.getClass();
			var17 = var10000.nextInt(128);
			var20 = var5 + this.random.nextInt(16) + 8;
			(new WorldGenFlowers(Block.plantYellow.blockID)).generate(this.worldObj, this.random, var16, var17, var20);
		}

		if(this.random.nextInt(2) == 0) {
			var15 = var4 + this.random.nextInt(16) + 8;
			var10000 = this.random;
			this.worldObj.getClass();
			var16 = var10000.nextInt(128);
			var17 = var5 + this.random.nextInt(16) + 8;
			(new WorldGenFlowers(Block.plantRed.blockID)).generate(this.worldObj, this.random, var15, var16, var17);
		}

		if(this.random.nextInt(4) == 0) {
			var15 = var4 + this.random.nextInt(16) + 8;
			var10000 = this.random;
			this.worldObj.getClass();
			var16 = var10000.nextInt(128);
			var17 = var5 + this.random.nextInt(16) + 8;
			(new WorldGenFlowers(Block.mushroomBrown.blockID)).generate(this.worldObj, this.random, var15, var16, var17);
		}

		if(this.random.nextInt(8) == 0) {
			var15 = var4 + this.random.nextInt(16) + 8;
			var10000 = this.random;
			this.worldObj.getClass();
			var16 = var10000.nextInt(128);
			var17 = var5 + this.random.nextInt(16) + 8;
			(new WorldGenFlowers(Block.mushroomRed.blockID)).generate(this.worldObj, this.random, var15, var16, var17);
		}

		for(var15 = 0; var15 < 10; ++var15) {
			var16 = var4 + this.random.nextInt(16) + 8;
			var10000 = this.random;
			this.worldObj.getClass();
			var17 = var10000.nextInt(128);
			var20 = var5 + this.random.nextInt(16) + 8;
			(new WorldGenReed()).generate(this.worldObj, this.random, var16, var17, var20);
		}

		if(this.random.nextInt(32) == 0) {
			var15 = var4 + this.random.nextInt(16) + 8;
			var10000 = this.random;
			this.worldObj.getClass();
			var16 = var10000.nextInt(128);
			var17 = var5 + this.random.nextInt(16) + 8;
			(new WorldGenPumpkin()).generate(this.worldObj, this.random, var15, var16, var17);
		}

		var15 = 0;
		if(var6 == BiomeGenBase.desert) {
			var15 += 10;
		}

		int var19;
		for(var16 = 0; var16 < var15; ++var16) {
			var17 = var4 + this.random.nextInt(16) + 8;
			var10000 = this.random;
			this.worldObj.getClass();
			var20 = var10000.nextInt(128);
			var19 = var5 + this.random.nextInt(16) + 8;
			(new WorldGenCactus()).generate(this.worldObj, this.random, var17, var20, var19);
		}

		for(var16 = 0; var16 < 50; ++var16) {
			var17 = var4 + this.random.nextInt(16) + 8;
			var10000 = this.random;
			var10001 = this.random;
			this.worldObj.getClass();
			var20 = var10000.nextInt(var10001.nextInt(128 - 8) + 8);
			var19 = var5 + this.random.nextInt(16) + 8;
			(new WorldGenLiquids(Block.waterMoving.blockID)).generate(this.worldObj, this.random, var17, var20, var19);
		}

		for(var16 = 0; var16 < 20; ++var16) {
			var17 = var4 + this.random.nextInt(16) + 8;
			var10000 = this.random;
			var10001 = this.random;
			Random var10002 = this.random;
			this.worldObj.getClass();
			var20 = var10000.nextInt(var10001.nextInt(var10002.nextInt(128 - 16) + 8) + 8);
			var19 = var5 + this.random.nextInt(16) + 8;
			(new WorldGenLiquids(Block.lavaMoving.blockID)).generate(this.worldObj, this.random, var17, var20, var19);
		}

		BlockSand.fallInstantly = false;
	}

	public boolean saveChunks(boolean var1, IProgressUpdate var2) {
		return true;
	}

	public boolean unload100OldestChunks() {
		return false;
	}

	public boolean canSave() {
		return true;
	}

	public String makeString() {
		return "RandomLevelSource";
	}
}
