package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentVillageHouse1 extends ComponentVillage {
	private int field_35398_a = -1;

	public ComponentVillageHouse1(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35316_g = var3;
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
	}

	public static ComponentVillageHouse1 func_35397_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, 0, 0, 0, 9, 9, 6, var5);
		return func_35366_a(var7) && StructureComponent.func_35312_a(var0, var7) == null ? new ComponentVillageHouse1(var6, var1, var7, var5) : null;
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.field_35398_a < 0) {
			this.field_35398_a = this.func_35367_b(var1, var3);
			if(this.field_35398_a < 0) {
				return true;
			}

			this.field_35316_g.func_35670_a(0, this.field_35398_a - this.field_35316_g.field_35675_e + 9 - 1, 0);
		}

		this.func_35294_a(var1, var3, 1, 1, 1, 7, 5, 4, 0, 0, false);
		this.func_35294_a(var1, var3, 0, 0, 0, 8, 0, 5, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 0, 5, 0, 8, 5, 5, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 0, 6, 1, 8, 6, 4, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 0, 7, 2, 8, 7, 3, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		int var4 = this.func_35301_c(Block.stairCompactPlanks.blockID, 3);
		int var5 = this.func_35301_c(Block.stairCompactPlanks.blockID, 2);

		int var6;
		int var7;
		for(var6 = -1; var6 <= 2; ++var6) {
			for(var7 = 0; var7 <= 8; ++var7) {
				this.func_35309_a(var1, Block.stairCompactPlanks.blockID, var4, var7, 6 + var6, var6, var3);
				this.func_35309_a(var1, Block.stairCompactPlanks.blockID, var5, var7, 6 + var6, 5 - var6, var3);
			}
		}

		this.func_35294_a(var1, var3, 0, 1, 0, 0, 1, 5, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 1, 1, 5, 8, 1, 5, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 8, 1, 0, 8, 1, 4, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 2, 1, 0, 7, 1, 0, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 0, 2, 0, 0, 4, 0, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 0, 2, 5, 0, 4, 5, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 8, 2, 5, 8, 4, 5, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 8, 2, 0, 8, 4, 0, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 0, 2, 1, 0, 4, 4, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 1, 2, 5, 7, 4, 5, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 8, 2, 1, 8, 4, 4, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 1, 2, 0, 7, 4, 0, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 4, 2, 0, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 5, 2, 0, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 6, 2, 0, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 4, 3, 0, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 5, 3, 0, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 6, 3, 0, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 2, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 2, 3, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 3, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 3, 3, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 8, 2, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 8, 2, 3, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 8, 3, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 8, 3, 3, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 2, 2, 5, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 3, 2, 5, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 5, 2, 5, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 6, 2, 5, var3);
		this.func_35294_a(var1, var3, 1, 4, 1, 7, 4, 1, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 1, 4, 4, 7, 4, 4, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 1, 3, 4, 7, 3, 4, Block.bookShelf.blockID, Block.bookShelf.blockID, false);
		this.func_35309_a(var1, Block.planks.blockID, 0, 7, 1, 4, var3);
		this.func_35309_a(var1, Block.stairCompactPlanks.blockID, this.func_35301_c(Block.stairCompactPlanks.blockID, 0), 7, 1, 3, var3);
		var6 = this.func_35301_c(Block.stairCompactPlanks.blockID, 3);
		this.func_35309_a(var1, Block.stairCompactPlanks.blockID, var6, 6, 1, 4, var3);
		this.func_35309_a(var1, Block.stairCompactPlanks.blockID, var6, 5, 1, 4, var3);
		this.func_35309_a(var1, Block.stairCompactPlanks.blockID, var6, 4, 1, 4, var3);
		this.func_35309_a(var1, Block.stairCompactPlanks.blockID, var6, 3, 1, 4, var3);
		this.func_35309_a(var1, Block.fence.blockID, 0, 6, 1, 3, var3);
		this.func_35309_a(var1, Block.pressurePlatePlanks.blockID, 0, 6, 2, 3, var3);
		this.func_35309_a(var1, Block.fence.blockID, 0, 4, 1, 3, var3);
		this.func_35309_a(var1, Block.pressurePlatePlanks.blockID, 0, 4, 2, 3, var3);
		this.func_35309_a(var1, Block.workbench.blockID, 0, 7, 1, 1, var3);
		this.func_35309_a(var1, 0, 0, 1, 1, 0, var3);
		this.func_35309_a(var1, 0, 0, 1, 2, 0, var3);
		this.func_35298_a(var1, var3, var2, 1, 1, 0, this.func_35301_c(Block.doorWood.blockID, 1));
		if(this.func_35297_a(var1, 1, 0, -1, var3) == 0 && this.func_35297_a(var1, 1, -1, -1, var3) != 0) {
			this.func_35309_a(var1, Block.stairCompactCobblestone.blockID, this.func_35301_c(Block.stairCompactCobblestone.blockID, 3), 1, 0, -1, var3);
		}

		for(var7 = 0; var7 < 6; ++var7) {
			for(int var8 = 0; var8 < 9; ++var8) {
				this.func_35314_b(var1, var8, 9, var7, var3);
				this.func_35303_b(var1, Block.cobblestone.blockID, 0, var8, -1, var7, var3);
			}
		}

		return true;
	}
}
