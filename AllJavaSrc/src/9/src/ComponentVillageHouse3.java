package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentVillageHouse3 extends ComponentVillage {
	private int field_35102_a = -1;

	public ComponentVillageHouse3(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35025_h = var4;
		this.field_35024_g = var3;
	}

	public void func_35004_a(StructureComponent var1, List var2, Random var3) {
	}

	public static ComponentVillageHouse3 func_35101_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35747_a(var2, var3, var4, 0, 0, 0, 9, 7, 12, var5);
		return func_35074_a(var7) && StructureComponent.func_35020_a(var0, var7) == null ? new ComponentVillageHouse3(var6, var1, var7, var5) : null;
	}

	public boolean func_35023_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.field_35102_a < 0) {
			this.field_35102_a = this.func_35075_b(var1, var3);
			if(this.field_35102_a < 0) {
				return true;
			}

			this.field_35024_g.func_35745_a(0, this.field_35102_a - this.field_35024_g.field_35750_e + 7 - 1, 0);
		}

		this.func_35011_a(var1, var3, 1, 1, 1, 7, 4, 4, 0, 0, false);
		this.func_35011_a(var1, var3, 2, 1, 6, 8, 4, 10, 0, 0, false);
		this.func_35011_a(var1, var3, 2, 0, 5, 8, 0, 10, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35011_a(var1, var3, 1, 0, 1, 7, 0, 4, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35011_a(var1, var3, 0, 0, 0, 0, 3, 5, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35011_a(var1, var3, 8, 0, 0, 8, 3, 10, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35011_a(var1, var3, 1, 0, 0, 7, 2, 0, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35011_a(var1, var3, 1, 0, 5, 2, 1, 5, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35011_a(var1, var3, 2, 0, 6, 2, 3, 10, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35011_a(var1, var3, 3, 0, 10, 7, 3, 10, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35011_a(var1, var3, 1, 2, 0, 7, 3, 0, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35011_a(var1, var3, 1, 2, 5, 2, 3, 5, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35011_a(var1, var3, 0, 4, 1, 8, 4, 1, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35011_a(var1, var3, 0, 4, 4, 3, 4, 4, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35011_a(var1, var3, 0, 5, 2, 8, 5, 3, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35018_a(var1, Block.planks.blockID, 0, 0, 4, 2, var3);
		this.func_35018_a(var1, Block.planks.blockID, 0, 0, 4, 3, var3);
		this.func_35018_a(var1, Block.planks.blockID, 0, 8, 4, 2, var3);
		this.func_35018_a(var1, Block.planks.blockID, 0, 8, 4, 3, var3);
		this.func_35018_a(var1, Block.planks.blockID, 0, 8, 4, 4, var3);
		int var4 = this.func_35009_c(Block.stairCompactPlanks.blockID, 3);
		int var5 = this.func_35009_c(Block.stairCompactPlanks.blockID, 2);

		int var6;
		int var7;
		for(var6 = -1; var6 <= 2; ++var6) {
			for(var7 = 0; var7 <= 8; ++var7) {
				this.func_35018_a(var1, Block.stairCompactPlanks.blockID, var4, var7, 4 + var6, var6, var3);
				if((var6 > -1 || var7 <= 1) && (var6 > 0 || var7 <= 3) && (var6 > 1 || var7 <= 4 || var7 >= 6)) {
					this.func_35018_a(var1, Block.stairCompactPlanks.blockID, var5, var7, 4 + var6, 5 - var6, var3);
				}
			}
		}

		this.func_35011_a(var1, var3, 3, 4, 5, 3, 4, 10, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35011_a(var1, var3, 7, 4, 2, 7, 4, 10, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35011_a(var1, var3, 4, 5, 4, 4, 5, 10, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35011_a(var1, var3, 6, 5, 4, 6, 5, 10, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35011_a(var1, var3, 5, 6, 3, 5, 6, 10, Block.planks.blockID, Block.planks.blockID, false);
		var6 = this.func_35009_c(Block.stairCompactPlanks.blockID, 0);

		int var8;
		for(var7 = 4; var7 >= 1; --var7) {
			this.func_35018_a(var1, Block.planks.blockID, 0, var7, 2 + var7, 7 - var7, var3);

			for(var8 = 8 - var7; var8 <= 10; ++var8) {
				this.func_35018_a(var1, Block.stairCompactPlanks.blockID, var6, var7, 2 + var7, var8, var3);
			}
		}

		var7 = this.func_35009_c(Block.stairCompactPlanks.blockID, 1);
		this.func_35018_a(var1, Block.planks.blockID, 0, 6, 6, 3, var3);
		this.func_35018_a(var1, Block.planks.blockID, 0, 7, 5, 4, var3);
		this.func_35018_a(var1, Block.stairCompactPlanks.blockID, var7, 6, 6, 4, var3);

		int var9;
		for(var8 = 6; var8 <= 8; ++var8) {
			for(var9 = 5; var9 <= 10; ++var9) {
				this.func_35018_a(var1, Block.stairCompactPlanks.blockID, var7, var8, 12 - var8, var9, var3);
			}
		}

		this.func_35018_a(var1, Block.wood.blockID, 0, 0, 2, 1, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 0, 2, 4, var3);
		this.func_35018_a(var1, Block.field_35282_br.blockID, 0, 0, 2, 2, var3);
		this.func_35018_a(var1, Block.field_35282_br.blockID, 0, 0, 2, 3, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 4, 2, 0, var3);
		this.func_35018_a(var1, Block.field_35282_br.blockID, 0, 5, 2, 0, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 6, 2, 0, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 8, 2, 1, var3);
		this.func_35018_a(var1, Block.field_35282_br.blockID, 0, 8, 2, 2, var3);
		this.func_35018_a(var1, Block.field_35282_br.blockID, 0, 8, 2, 3, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 8, 2, 4, var3);
		this.func_35018_a(var1, Block.planks.blockID, 0, 8, 2, 5, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 8, 2, 6, var3);
		this.func_35018_a(var1, Block.field_35282_br.blockID, 0, 8, 2, 7, var3);
		this.func_35018_a(var1, Block.field_35282_br.blockID, 0, 8, 2, 8, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 8, 2, 9, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 2, 2, 6, var3);
		this.func_35018_a(var1, Block.field_35282_br.blockID, 0, 2, 2, 7, var3);
		this.func_35018_a(var1, Block.field_35282_br.blockID, 0, 2, 2, 8, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 2, 2, 9, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 4, 4, 10, var3);
		this.func_35018_a(var1, Block.field_35282_br.blockID, 0, 5, 4, 10, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 6, 4, 10, var3);
		this.func_35018_a(var1, Block.planks.blockID, 0, 5, 5, 10, var3);
		this.func_35018_a(var1, 0, 0, 2, 1, 0, var3);
		this.func_35018_a(var1, 0, 0, 2, 2, 0, var3);
		this.func_35018_a(var1, Block.torchWood.blockID, 0, 2, 3, 1, var3);
		this.func_35002_a(var1, var3, var2, 2, 1, 0, this.func_35009_c(Block.doorWood.blockID, 1));
		this.func_35011_a(var1, var3, 1, 0, -1, 3, 2, -1, 0, 0, false);
		if(this.func_35007_a(var1, 2, 0, -1, var3) == 0 && this.func_35007_a(var1, 2, -1, -1, var3) != 0) {
			this.func_35018_a(var1, Block.stairCompactCobblestone.blockID, this.func_35009_c(Block.stairCompactCobblestone.blockID, 3), 2, 0, -1, var3);
		}

		for(var8 = 0; var8 < 5; ++var8) {
			for(var9 = 0; var9 < 9; ++var9) {
				this.func_35016_b(var1, var9, 7, var8, var3);
				this.func_35005_b(var1, Block.cobblestone.blockID, 0, var9, -1, var8, var3);
			}
		}

		for(var8 = 5; var8 < 11; ++var8) {
			for(var9 = 2; var9 < 9; ++var9) {
				this.func_35016_b(var1, var9, 7, var8, var3);
				this.func_35005_b(var1, Block.cobblestone.blockID, 0, var9, -1, var8, var3);
			}
		}

		return true;
	}
}
