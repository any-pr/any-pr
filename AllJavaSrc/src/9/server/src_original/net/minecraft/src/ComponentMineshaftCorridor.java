package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentMineshaftCorridor extends StructureComponent {
	private final boolean field_35361_a;
	private final boolean field_35359_b;
	private boolean field_35360_c;
	private int field_35358_d;

	public ComponentMineshaftCorridor(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35316_g = var3;
		this.field_35361_a = var2.nextInt(3) == 0;
		this.field_35359_b = !this.field_35361_a && var2.nextInt(23) == 0;
		if(this.field_35317_h != 2 && this.field_35317_h != 0) {
			this.field_35358_d = var3.func_35669_b() / 5;
		} else {
			this.field_35358_d = var3.func_35665_d() / 5;
		}

	}

	public static StructureBoundingBox func_35357_a(List var0, Random var1, int var2, int var3, int var4, int var5) {
		StructureBoundingBox var6 = new StructureBoundingBox(var2, var3, var4, var2, var3 + 2, var4);

		int var7;
		for(var7 = var1.nextInt(3) + 2; var7 > 0; --var7) {
			int var8 = var7 * 5;
			switch(var5) {
			case 0:
				var6.field_35674_d = var2 + 2;
				var6.field_35673_f = var4 + (var8 - 1);
				break;
			case 1:
				var6.field_35678_a = var2 - (var8 - 1);
				var6.field_35673_f = var4 + 2;
				break;
			case 2:
				var6.field_35674_d = var2 + 2;
				var6.field_35677_c = var4 - (var8 - 1);
				break;
			case 3:
				var6.field_35674_d = var2 + (var8 - 1);
				var6.field_35673_f = var4 + 2;
			}

			if(StructureComponent.func_35312_a(var0, var6) == null) {
				break;
			}
		}

		return var7 > 0 ? var6 : null;
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
		int var4 = this.func_35305_c();
		int var5 = var3.nextInt(4);
		switch(this.field_35317_h) {
		case 0:
			if(var5 <= 1) {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a, this.field_35316_g.field_35676_b - 1 + var3.nextInt(3), this.field_35316_g.field_35673_f + 1, this.field_35317_h, var4);
			} else if(var5 == 2) {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a - 1, this.field_35316_g.field_35676_b - 1 + var3.nextInt(3), this.field_35316_g.field_35673_f - 3, 1, var4);
			} else {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35674_d + 1, this.field_35316_g.field_35676_b - 1 + var3.nextInt(3), this.field_35316_g.field_35673_f - 3, 3, var4);
			}
			break;
		case 1:
			if(var5 <= 1) {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a - 1, this.field_35316_g.field_35676_b - 1 + var3.nextInt(3), this.field_35316_g.field_35677_c, this.field_35317_h, var4);
			} else if(var5 == 2) {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a, this.field_35316_g.field_35676_b - 1 + var3.nextInt(3), this.field_35316_g.field_35677_c - 1, 2, var4);
			} else {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a, this.field_35316_g.field_35676_b - 1 + var3.nextInt(3), this.field_35316_g.field_35673_f + 1, 0, var4);
			}
			break;
		case 2:
			if(var5 <= 1) {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a, this.field_35316_g.field_35676_b - 1 + var3.nextInt(3), this.field_35316_g.field_35677_c - 1, this.field_35317_h, var4);
			} else if(var5 == 2) {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a - 1, this.field_35316_g.field_35676_b - 1 + var3.nextInt(3), this.field_35316_g.field_35677_c, 1, var4);
			} else {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35674_d + 1, this.field_35316_g.field_35676_b - 1 + var3.nextInt(3), this.field_35316_g.field_35677_c, 3, var4);
			}
			break;
		case 3:
			if(var5 <= 1) {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35674_d + 1, this.field_35316_g.field_35676_b - 1 + var3.nextInt(3), this.field_35316_g.field_35677_c, this.field_35317_h, var4);
			} else if(var5 == 2) {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35674_d - 3, this.field_35316_g.field_35676_b - 1 + var3.nextInt(3), this.field_35316_g.field_35677_c - 1, 2, var4);
			} else {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35674_d - 3, this.field_35316_g.field_35676_b - 1 + var3.nextInt(3), this.field_35316_g.field_35673_f + 1, 0, var4);
			}
		}

		if(var4 < 10) {
			int var6;
			int var7;
			if(this.field_35317_h != 2 && this.field_35317_h != 0) {
				for(var6 = this.field_35316_g.field_35678_a + 3; var6 + 3 <= this.field_35316_g.field_35674_d; var6 += 5) {
					var7 = var3.nextInt(5);
					if(var7 == 0) {
						StructureMineshaftPieces.func_35433_a(var1, var2, var3, var6, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c - 1, 2, var4 + 1);
					} else if(var7 == 1) {
						StructureMineshaftPieces.func_35433_a(var1, var2, var3, var6, this.field_35316_g.field_35676_b, this.field_35316_g.field_35673_f + 1, 0, var4 + 1);
					}
				}
			} else {
				for(var6 = this.field_35316_g.field_35677_c + 3; var6 + 3 <= this.field_35316_g.field_35673_f; var6 += 5) {
					var7 = var3.nextInt(5);
					if(var7 == 0) {
						StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a - 1, this.field_35316_g.field_35676_b, var6, 1, var4 + 1);
					} else if(var7 == 1) {
						StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35674_d + 1, this.field_35316_g.field_35676_b, var6, 3, var4 + 1);
					}
				}
			}
		}

	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.func_35295_a(var1, var3)) {
			return false;
		} else {
			int var8 = this.field_35358_d * 5 - 1;
			this.func_35294_a(var1, var3, 0, 0, 0, 2, 1, var8, 0, 0, false);
			this.func_35315_a(var1, var3, var2, 0.8F, 0, 2, 0, 2, 2, var8, 0, 0, false);
			if(this.field_35359_b) {
				this.func_35315_a(var1, var3, var2, 0.6F, 0, 0, 0, 2, 1, var8, Block.web.blockID, 0, false);
			}

			int var9;
			int var10;
			for(var9 = 0; var9 < this.field_35358_d; ++var9) {
				var10 = 2 + var9 * 5;
				this.func_35294_a(var1, var3, 0, 0, var10, 0, 1, var10, Block.fence.blockID, 0, false);
				this.func_35294_a(var1, var3, 2, 0, var10, 2, 1, var10, Block.fence.blockID, 0, false);
				if(var2.nextInt(4) != 0) {
					this.func_35294_a(var1, var3, 0, 2, var10, 2, 2, var10, Block.planks.blockID, 0, false);
				} else {
					this.func_35294_a(var1, var3, 0, 2, var10, 0, 2, var10, Block.planks.blockID, 0, false);
					this.func_35294_a(var1, var3, 2, 2, var10, 2, 2, var10, Block.planks.blockID, 0, false);
				}

				this.func_35302_a(var1, var3, var2, 0.1F, 0, 2, var10 - 1, Block.web.blockID, 0);
				this.func_35302_a(var1, var3, var2, 0.1F, 2, 2, var10 - 1, Block.web.blockID, 0);
				this.func_35302_a(var1, var3, var2, 0.1F, 0, 2, var10 + 1, Block.web.blockID, 0);
				this.func_35302_a(var1, var3, var2, 0.1F, 2, 2, var10 + 1, Block.web.blockID, 0);
				this.func_35302_a(var1, var3, var2, 0.05F, 0, 2, var10 - 2, Block.web.blockID, 0);
				this.func_35302_a(var1, var3, var2, 0.05F, 2, 2, var10 - 2, Block.web.blockID, 0);
				this.func_35302_a(var1, var3, var2, 0.05F, 0, 2, var10 + 2, Block.web.blockID, 0);
				this.func_35302_a(var1, var3, var2, 0.05F, 2, 2, var10 + 2, Block.web.blockID, 0);
				this.func_35302_a(var1, var3, var2, 0.05F, 1, 2, var10 - 1, Block.torchWood.blockID, 0);
				this.func_35302_a(var1, var3, var2, 0.05F, 1, 2, var10 + 1, Block.torchWood.blockID, 0);
				if(var2.nextInt(100) == 0) {
					this.func_35299_a(var1, var3, var2, 2, 0, var10 - 1, StructureMineshaftPieces.func_35432_a(), 3 + var2.nextInt(4));
				}

				if(var2.nextInt(100) == 0) {
					this.func_35299_a(var1, var3, var2, 0, 0, var10 + 1, StructureMineshaftPieces.func_35432_a(), 3 + var2.nextInt(4));
				}

				if(this.field_35359_b && !this.field_35360_c) {
					int var11 = this.func_35300_a(0);
					int var12 = var10 - 1 + var2.nextInt(3);
					int var13 = this.func_35306_a(1, var12);
					var12 = this.func_35296_b(1, var12);
					if(var3.func_35667_b(var13, var11, var12)) {
						this.field_35360_c = true;
						var1.setBlockWithNotify(var13, var11, var12, Block.mobSpawner.blockID);
						TileEntityMobSpawner var14 = (TileEntityMobSpawner)var1.getBlockTileEntity(var13, var11, var12);
						if(var14 != null) {
							var14.setMobID("CaveSpider");
						}
					}
				}
			}

			if(this.field_35361_a) {
				for(var9 = 0; var9 <= var8; ++var9) {
					var10 = this.func_35297_a(var1, 1, -1, var9, var3);
					if(var10 > 0 && Block.opaqueCubeLookup[var10]) {
						this.func_35302_a(var1, var3, var2, 0.7F, 1, 0, var9, Block.rail.blockID, this.func_35301_c(Block.rail.blockID, 0));
					}
				}
			}

			return true;
		}
	}
}
