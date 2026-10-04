package net.minecraft.src;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

public abstract class StructureComponent {
	protected StructureBoundingBox field_35024_g;
	protected int field_35025_h;
	protected int field_35026_i;

	protected StructureComponent(int var1) {
		this.field_35026_i = var1;
		this.field_35025_h = -1;
	}

	public void func_35004_a(StructureComponent var1, List var2, Random var3) {
	}

	public abstract boolean func_35023_a(World var1, Random var2, StructureBoundingBox var3);

	public StructureBoundingBox func_35021_b() {
		return this.field_35024_g;
	}

	public int func_35012_c() {
		return this.field_35026_i;
	}

	public static StructureComponent func_35020_a(List var0, StructureBoundingBox var1) {
		Iterator var2 = var0.iterator();

		StructureComponent var3;
		do {
			if(!var2.hasNext()) {
				return null;
			}

			var3 = (StructureComponent)var2.next();
		} while(var3.func_35021_b() == null || !var3.func_35021_b().func_35740_a(var1));

		return var3;
	}

	protected boolean func_35013_a(World var1, StructureBoundingBox var2) {
		int var3 = Math.max(this.field_35024_g.field_35753_a - 1, var2.field_35753_a);
		int var4 = Math.max(this.field_35024_g.field_35751_b - 1, var2.field_35751_b);
		int var5 = Math.max(this.field_35024_g.field_35752_c - 1, var2.field_35752_c);
		int var6 = Math.min(this.field_35024_g.field_35749_d + 1, var2.field_35749_d);
		int var7 = Math.min(this.field_35024_g.field_35750_e + 1, var2.field_35750_e);
		int var8 = Math.min(this.field_35024_g.field_35748_f + 1, var2.field_35748_f);

		int var9;
		int var10;
		int var11;
		for(var9 = var3; var9 <= var6; ++var9) {
			for(var10 = var5; var10 <= var8; ++var10) {
				var11 = var1.getBlockId(var9, var4, var10);
				if(var11 > 0 && Block.blocksList[var11].blockMaterial.getIsLiquid()) {
					return true;
				}

				var11 = var1.getBlockId(var9, var7, var10);
				if(var11 > 0 && Block.blocksList[var11].blockMaterial.getIsLiquid()) {
					return true;
				}
			}
		}

		for(var9 = var3; var9 <= var6; ++var9) {
			for(var10 = var4; var10 <= var7; ++var10) {
				var11 = var1.getBlockId(var9, var10, var5);
				if(var11 > 0 && Block.blocksList[var11].blockMaterial.getIsLiquid()) {
					return true;
				}

				var11 = var1.getBlockId(var9, var10, var8);
				if(var11 > 0 && Block.blocksList[var11].blockMaterial.getIsLiquid()) {
					return true;
				}
			}
		}

		for(var9 = var5; var9 <= var8; ++var9) {
			for(var10 = var4; var10 <= var7; ++var10) {
				var11 = var1.getBlockId(var3, var10, var9);
				if(var11 > 0 && Block.blocksList[var11].blockMaterial.getIsLiquid()) {
					return true;
				}

				var11 = var1.getBlockId(var6, var10, var9);
				if(var11 > 0 && Block.blocksList[var11].blockMaterial.getIsLiquid()) {
					return true;
				}
			}
		}

		return false;
	}

	protected int func_35017_a(int var1, int var2) {
		switch(this.field_35025_h) {
		case 0:
		case 2:
			return this.field_35024_g.field_35753_a + var1;
		case 1:
			return this.field_35024_g.field_35749_d - var2;
		case 3:
			return this.field_35024_g.field_35753_a + var2;
		default:
			return var1;
		}
	}

	protected int func_35008_a(int var1) {
		return this.field_35025_h == -1 ? var1 : var1 + this.field_35024_g.field_35751_b;
	}

	protected int func_35006_b(int var1, int var2) {
		switch(this.field_35025_h) {
		case 0:
			return this.field_35024_g.field_35752_c + var2;
		case 1:
		case 3:
			return this.field_35024_g.field_35752_c + var1;
		case 2:
			return this.field_35024_g.field_35748_f - var2;
		default:
			return var2;
		}
	}

	protected int func_35009_c(int var1, int var2) {
		if(var1 == Block.rail.blockID) {
			if(this.field_35025_h == 1 || this.field_35025_h == 3) {
				if(var2 == 1) {
					return 0;
				}

				return 1;
			}
		} else if(var1 != Block.doorWood.blockID && var1 != Block.doorSteel.blockID) {
			if(var1 != Block.stairCompactCobblestone.blockID && var1 != Block.stairCompactPlanks.blockID) {
				if(var1 == Block.ladder.blockID) {
					if(this.field_35025_h == 0) {
						if(var2 == 2) {
							return 3;
						}

						if(var2 == 3) {
							return 2;
						}
					} else if(this.field_35025_h == 1) {
						if(var2 == 2) {
							return 4;
						}

						if(var2 == 3) {
							return 5;
						}

						if(var2 == 4) {
							return 2;
						}

						if(var2 == 5) {
							return 3;
						}
					} else if(this.field_35025_h == 3) {
						if(var2 == 2) {
							return 5;
						}

						if(var2 == 3) {
							return 4;
						}

						if(var2 == 4) {
							return 2;
						}

						if(var2 == 5) {
							return 3;
						}
					}
				} else if(var1 == Block.button.blockID) {
					if(this.field_35025_h == 0) {
						if(var2 == 3) {
							return 4;
						}

						if(var2 == 4) {
							return 3;
						}
					} else if(this.field_35025_h == 1) {
						if(var2 == 3) {
							return 1;
						}

						if(var2 == 4) {
							return 2;
						}

						if(var2 == 2) {
							return 3;
						}

						if(var2 == 1) {
							return 4;
						}
					} else if(this.field_35025_h == 3) {
						if(var2 == 3) {
							return 2;
						}

						if(var2 == 4) {
							return 1;
						}

						if(var2 == 2) {
							return 3;
						}

						if(var2 == 1) {
							return 4;
						}
					}
				}
			} else if(this.field_35025_h == 0) {
				if(var2 == 2) {
					return 3;
				}

				if(var2 == 3) {
					return 2;
				}
			} else if(this.field_35025_h == 1) {
				if(var2 == 0) {
					return 2;
				}

				if(var2 == 1) {
					return 3;
				}

				if(var2 == 2) {
					return 0;
				}

				if(var2 == 3) {
					return 1;
				}
			} else if(this.field_35025_h == 3) {
				if(var2 == 0) {
					return 2;
				}

				if(var2 == 1) {
					return 3;
				}

				if(var2 == 2) {
					return 1;
				}

				if(var2 == 3) {
					return 0;
				}
			}
		} else if(this.field_35025_h == 0) {
			if(var2 == 0) {
				return 2;
			}

			if(var2 == 2) {
				return 0;
			}
		} else {
			if(this.field_35025_h == 1) {
				return var2 + 1 & 3;
			}

			if(this.field_35025_h == 3) {
				return var2 + 3 & 3;
			}
		}

		return var2;
	}

	protected void func_35018_a(World var1, int var2, int var3, int var4, int var5, int var6, StructureBoundingBox var7) {
		int var8 = this.func_35017_a(var4, var6);
		int var9 = this.func_35008_a(var5);
		int var10 = this.func_35006_b(var4, var6);
		if(var7.func_35742_b(var8, var9, var10)) {
			var1.setBlockAndMetadata(var8, var9, var10, var2, var3);
		}
	}

	protected int func_35007_a(World var1, int var2, int var3, int var4, StructureBoundingBox var5) {
		int var6 = this.func_35017_a(var2, var4);
		int var7 = this.func_35008_a(var3);
		int var8 = this.func_35006_b(var2, var4);
		return !var5.func_35742_b(var6, var7, var8) ? 0 : var1.getBlockId(var6, var7, var8);
	}

	protected void func_35011_a(World var1, StructureBoundingBox var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9, int var10, boolean var11) {
		for(int var12 = var4; var12 <= var7; ++var12) {
			for(int var13 = var3; var13 <= var6; ++var13) {
				for(int var14 = var5; var14 <= var8; ++var14) {
					if(!var11 || this.func_35007_a(var1, var13, var12, var14, var2) != 0) {
						if(var12 != var4 && var12 != var7 && var13 != var3 && var13 != var6 && var14 != var5 && var14 != var8) {
							this.func_35018_a(var1, var10, 0, var13, var12, var14, var2);
						} else {
							this.func_35018_a(var1, var9, 0, var13, var12, var14, var2);
						}
					}
				}
			}
		}

	}

	protected void func_35022_a(World var1, StructureBoundingBox var2, int var3, int var4, int var5, int var6, int var7, int var8, boolean var9, Random var10, StructurePieceBlockSelector var11) {
		for(int var12 = var4; var12 <= var7; ++var12) {
			for(int var13 = var3; var13 <= var6; ++var13) {
				for(int var14 = var5; var14 <= var8; ++var14) {
					if(!var9 || this.func_35007_a(var1, var13, var12, var14, var2) != 0) {
						var11.func_35706_a(var10, var13, var12, var14, var12 == var4 || var12 == var7 || var13 == var3 || var13 == var6 || var14 == var5 || var14 == var8);
						this.func_35018_a(var1, var11.func_35707_a(), var11.func_35708_b(), var13, var12, var14, var2);
					}
				}
			}
		}

	}

	protected void func_35010_a(World var1, StructureBoundingBox var2, Random var3, float var4, int var5, int var6, int var7, int var8, int var9, int var10, int var11, int var12, boolean var13) {
		for(int var14 = var6; var14 <= var9; ++var14) {
			for(int var15 = var5; var15 <= var8; ++var15) {
				for(int var16 = var7; var16 <= var10; ++var16) {
					if(var3.nextFloat() <= var4 && (!var13 || this.func_35007_a(var1, var15, var14, var16, var2) != 0)) {
						if(var14 != var6 && var14 != var9 && var15 != var5 && var15 != var8 && var16 != var7 && var16 != var10) {
							this.func_35018_a(var1, var12, 0, var15, var14, var16, var2);
						} else {
							this.func_35018_a(var1, var11, 0, var15, var14, var16, var2);
						}
					}
				}
			}
		}

	}

	protected void func_35014_a(World var1, StructureBoundingBox var2, Random var3, float var4, int var5, int var6, int var7, int var8, int var9) {
		if(var3.nextFloat() < var4) {
			this.func_35018_a(var1, var8, var9, var5, var6, var7, var2);
		}

	}

	protected void func_35015_a(World var1, StructureBoundingBox var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9, boolean var10) {
		float var11 = (float)(var6 - var3 + 1);
		float var12 = (float)(var7 - var4 + 1);
		float var13 = (float)(var8 - var5 + 1);
		float var14 = (float)var3 + var11 / 2.0F;
		float var15 = (float)var5 + var13 / 2.0F;

		for(int var16 = var4; var16 <= var7; ++var16) {
			float var17 = (float)(var16 - var4) / var12;

			for(int var18 = var3; var18 <= var6; ++var18) {
				float var19 = ((float)var18 - var14) / (var11 * 0.5F);

				for(int var20 = var5; var20 <= var8; ++var20) {
					float var21 = ((float)var20 - var15) / (var13 * 0.5F);
					if(!var10 || this.func_35007_a(var1, var18, var16, var20, var2) != 0) {
						float var22 = var19 * var19 + var17 * var17 + var21 * var21;
						if(var22 <= 1.05F) {
							this.func_35018_a(var1, var9, 0, var18, var16, var20, var2);
						}
					}
				}
			}
		}

	}

	protected void func_35016_b(World var1, int var2, int var3, int var4, StructureBoundingBox var5) {
		int var6 = this.func_35017_a(var2, var4);
		int var7 = this.func_35008_a(var3);
		int var8 = this.func_35006_b(var2, var4);
		if(var5.func_35742_b(var6, var7, var8)) {
			while(!var1.isAirBlock(var6, var7, var8)) {
				var1.getClass();
				if(var7 >= 127) {
					break;
				}

				var1.setBlockAndMetadata(var6, var7, var8, 0, 0);
				++var7;
			}

		}
	}

	protected void func_35005_b(World var1, int var2, int var3, int var4, int var5, int var6, StructureBoundingBox var7) {
		int var8 = this.func_35017_a(var4, var6);
		int var9 = this.func_35008_a(var5);
		int var10 = this.func_35006_b(var4, var6);
		if(var7.func_35742_b(var8, var9, var10)) {
			while((var1.isAirBlock(var8, var9, var10) || var1.getBlockMaterial(var8, var9, var10).getIsLiquid()) && var9 > 1) {
				var1.setBlockAndMetadata(var8, var9, var10, var2, var3);
				--var9;
			}

		}
	}

	protected void func_35003_a(World var1, StructureBoundingBox var2, Random var3, int var4, int var5, int var6, StructurePieceTreasure[] var7, int var8) {
		int var9 = this.func_35017_a(var4, var6);
		int var10 = this.func_35008_a(var5);
		int var11 = this.func_35006_b(var4, var6);
		if(var2.func_35742_b(var9, var10, var11) && var1.getBlockId(var9, var10, var11) != Block.chest.blockID) {
			var1.setBlockWithNotify(var9, var10, var11, Block.chest.blockID);
			TileEntityChest var12 = (TileEntityChest)var1.getBlockTileEntity(var9, var10, var11);
			if(var12 != null) {
				func_35019_a(var3, var7, var12, var8);
			}
		}

	}

	private static void func_35019_a(Random var0, StructurePieceTreasure[] var1, TileEntityChest var2, int var3) {
		for(int var4 = 0; var4 < var3; ++var4) {
			StructurePieceTreasure var5 = (StructurePieceTreasure)WeightedRandom.func_35735_a(var0, var1);
			int var6 = var5.field_35595_c + var0.nextInt(var5.field_35593_e - var5.field_35595_c + 1);
			if(Item.itemsList[var5.field_35596_a].getItemStackLimit() >= var6) {
				var2.setInventorySlotContents(var0.nextInt(var2.getSizeInventory()), new ItemStack(var5.field_35596_a, var6, var5.field_35594_b));
			} else {
				for(int var7 = 0; var7 < var6; ++var7) {
					var2.setInventorySlotContents(var0.nextInt(var2.getSizeInventory()), new ItemStack(var5.field_35596_a, 1, var5.field_35594_b));
				}
			}
		}

	}

	protected void func_35002_a(World var1, StructureBoundingBox var2, Random var3, int var4, int var5, int var6, int var7) {
		int var8 = this.func_35017_a(var4, var6);
		int var9 = this.func_35008_a(var5);
		int var10 = this.func_35006_b(var4, var6);
		if(var2.func_35742_b(var8, var9, var10)) {
			ItemDoor.func_35434_a(var1, var8, var9, var10, var7, Block.doorWood);
		}

	}
}
