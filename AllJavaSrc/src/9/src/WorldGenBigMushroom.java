package net.minecraft.src;

import java.util.Random;

public class WorldGenBigMushroom extends WorldGenerator {
	private int field_35266_a = -1;

	public WorldGenBigMushroom(int var1) {
		this.field_35266_a = var1;
	}

	public WorldGenBigMushroom() {
	}

	public boolean generate(World var1, Random var2, int var3, int var4, int var5) {
		int var6 = var2.nextInt(2);
		if(this.field_35266_a >= 0) {
			var6 = this.field_35266_a;
		}

		int var7 = var2.nextInt(3) + 4;
		boolean var8 = true;
		if(var4 >= 1) {
			int var10000 = var4 + var7 + 1;
			var1.getClass();
			if(var10000 <= 128) {
				int var9;
				int var11;
				int var12;
				int var13;
				for(var9 = var4; var9 <= var4 + 1 + var7; ++var9) {
					byte var10 = 3;
					if(var9 == var4) {
						var10 = 0;
					}

					for(var11 = var3 - var10; var11 <= var3 + var10 && var8; ++var11) {
						for(var12 = var5 - var10; var12 <= var5 + var10 && var8; ++var12) {
							if(var9 >= 0) {
								var1.getClass();
								if(var9 < 128) {
									var13 = var1.getBlockId(var11, var9, var12);
									if(var13 != 0 && var13 != Block.leaves.blockID) {
										var8 = false;
									}
									continue;
								}
							}

							var8 = false;
						}
					}
				}

				if(!var8) {
					return false;
				}

				if(!Block.mushroomBrown.canPlaceBlockAt(var1, var3, var4, var5)) {
					return false;
				}

				var1.setBlock(var3, var4 - 1, var5, Block.dirt.blockID);
				var9 = var4 + var7;
				if(var6 == 1) {
					var9 = var4 + var7 - 3;
				}

				int var15;
				for(var15 = var9; var15 <= var4 + var7; ++var15) {
					var11 = 1;
					if(var15 < var4 + var7) {
						++var11;
					}

					if(var6 == 0) {
						var11 = 3;
					}

					for(var12 = var3 - var11; var12 <= var3 + var11; ++var12) {
						for(var13 = var5 - var11; var13 <= var5 + var11; ++var13) {
							int var14 = 5;
							if(var12 == var3 - var11) {
								--var14;
							}

							if(var12 == var3 + var11) {
								++var14;
							}

							if(var13 == var5 - var11) {
								var14 -= 3;
							}

							if(var13 == var5 + var11) {
								var14 += 3;
							}

							if(var6 == 0 || var15 < var4 + var7) {
								if((var12 == var3 - var11 || var12 == var3 + var11) && (var13 == var5 - var11 || var13 == var5 + var11)) {
									continue;
								}

								if(var12 == var3 - (var11 - 1) && var13 == var5 - var11) {
									var14 = 1;
								}

								if(var12 == var3 - var11 && var13 == var5 - (var11 - 1)) {
									var14 = 1;
								}

								if(var12 == var3 + (var11 - 1) && var13 == var5 - var11) {
									var14 = 3;
								}

								if(var12 == var3 + var11 && var13 == var5 - (var11 - 1)) {
									var14 = 3;
								}

								if(var12 == var3 - (var11 - 1) && var13 == var5 + var11) {
									var14 = 7;
								}

								if(var12 == var3 - var11 && var13 == var5 + (var11 - 1)) {
									var14 = 7;
								}

								if(var12 == var3 + (var11 - 1) && var13 == var5 + var11) {
									var14 = 9;
								}

								if(var12 == var3 + var11 && var13 == var5 + (var11 - 1)) {
									var14 = 9;
								}
							}

							if(var14 == 5 && var15 < var4 + var7) {
								var14 = 0;
							}

							if((var14 != 0 || var4 >= var4 + var7 - 1) && !Block.opaqueCubeLookup[var1.getBlockId(var12, var15, var13)]) {
								var1.setBlockAndMetadata(var12, var15, var13, Block.field_35286_bo.blockID + var6, var14);
							}
						}
					}
				}

				for(var15 = 0; var15 < var7; ++var15) {
					var11 = var1.getBlockId(var3, var4 + var15, var5);
					if(!Block.opaqueCubeLookup[var11]) {
						var1.setBlockAndMetadata(var3, var4 + var15, var5, Block.field_35286_bo.blockID + var6, 10);
					}
				}

				return true;
			}
		}

		return false;
	}
}
