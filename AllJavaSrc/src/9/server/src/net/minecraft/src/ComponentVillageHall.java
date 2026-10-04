package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentVillageHall extends ComponentVillage {
	private int field_35375_a = -1;

	public ComponentVillageHall(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35316_g = var3;
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
	}

	public static ComponentVillageHall func_35374_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, 0, 0, 0, 9, 7, 11, var5);
		return func_35366_a(var7) && StructureComponent.func_35312_a(var0, var7) == null ? new ComponentVillageHall(var6, var1, var7, var5) : null;
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.field_35375_a < 0) {
			this.field_35375_a = this.func_35367_b(var1, var3);
			if(this.field_35375_a < 0) {
				return true;
			}

			this.field_35316_g.func_35670_a(0, this.field_35375_a - this.field_35316_g.field_35675_e + 7 - 1, 0);
		}

		this.func_35294_a(var1, var3, 1, 1, 1, 7, 4, 4, 0, 0, false);
		this.func_35294_a(var1, var3, 2, 1, 6, 8, 4, 10, 0, 0, false);
		this.func_35294_a(var1, var3, 2, 0, 6, 8, 0, 10, Block.dirt.blockID, Block.dirt.blockID, false);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 6, 0, 6, var3);
		this.func_35294_a(var1, var3, 2, 1, 6, 2, 1, 10, Block.fence.blockID, Block.fence.blockID, false);
		this.func_35294_a(var1, var3, 8, 1, 6, 8, 1, 10, Block.fence.blockID, Block.fence.blockID, false);
		this.func_35294_a(var1, var3, 3, 1, 10, 7, 1, 10, Block.fence.blockID, Block.fence.blockID, false);
		this.func_35294_a(var1, var3, 1, 0, 1, 7, 0, 4, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 0, 0, 0, 0, 3, 5, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 8, 0, 0, 8, 3, 5, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 1, 0, 0, 7, 1, 0, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 1, 0, 5, 7, 1, 5, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 1, 2, 0, 7, 3, 0, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 1, 2, 5, 7, 3, 5, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 0, 4, 1, 8, 4, 1, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 0, 4, 4, 8, 4, 4, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 0, 5, 2, 8, 5, 3, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35309_a(var1, Block.planks.blockID, 0, 0, 4, 2, var3);
		this.func_35309_a(var1, Block.planks.blockID, 0, 0, 4, 3, var3);
		this.func_35309_a(var1, Block.planks.blockID, 0, 8, 4, 2, var3);
		this.func_35309_a(var1, Block.planks.blockID, 0, 8, 4, 3, var3);
		int var4 = this.func_35301_c(Block.stairCompactPlanks.blockID, 3);
		int var5 = this.func_35301_c(Block.stairCompactPlanks.blockID, 2);

		int var6;
		int var7;
		for(var6 = -1; var6 <= 2; ++var6) {
			for(var7 = 0; var7 <= 8; ++var7) {
				this.func_35309_a(var1, Block.stairCompactPlanks.blockID, var4, var7, 4 + var6, var6, var3);
				this.func_35309_a(var1, Block.stairCompactPlanks.blockID, var5, var7, 4 + var6, 5 - var6, var3);
			}
		}

		this.func_35309_a(var1, Block.wood.blockID, 0, 0, 2, 1, var3);
		this.func_35309_a(var1, Block.wood.blockID, 0, 0, 2, 4, var3);
		this.func_35309_a(var1, Block.wood.blockID, 0, 8, 2, 1, var3);
		this.func_35309_a(var1, Block.wood.blockID, 0, 8, 2, 4, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 2, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 2, 3, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 8, 2, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 8, 2, 3, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 2, 2, 5, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 3, 2, 5, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 5, 2, 0, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 6, 2, 5, var3);
		this.func_35309_a(var1, Block.fence.blockID, 0, 2, 1, 3, var3);
		this.func_35309_a(var1, Block.pressurePlatePlanks.blockID, 0, 2, 2, 3, var3);
		this.func_35309_a(var1, Block.planks.blockID, 0, 1, 1, 4, var3);
		this.func_35309_a(var1, Block.stairCompactPlanks.blockID, this.func_35301_c(Block.stairCompactPlanks.blockID, 3), 2, 1, 4, var3);
		this.func_35309_a(var1, Block.stairCompactPlanks.blockID, this.func_35301_c(Block.stairCompactPlanks.blockID, 1), 1, 1, 3, var3);
		this.func_35294_a(var1, var3, 5, 0, 1, 7, 0, 3, Block.stairDouble.blockID, Block.stairDouble.blockID, false);
		this.func_35309_a(var1, Block.stairDouble.blockID, 0, 6, 1, 1, var3);
		this.func_35309_a(var1, Block.stairDouble.blockID, 0, 6, 1, 2, var3);
		this.func_35309_a(var1, 0, 0, 2, 1, 0, var3);
		this.func_35309_a(var1, 0, 0, 2, 2, 0, var3);
		this.func_35309_a(var1, Block.torchWood.blockID, 0, 2, 3, 1, var3);
		this.func_35298_a(var1, var3, var2, 2, 1, 0, this.func_35301_c(Block.doorWood.blockID, 1));
		if(this.func_35297_a(var1, 2, 0, -1, var3) == 0 && this.func_35297_a(var1, 2, -1, -1, var3) != 0) {
			this.func_35309_a(var1, Block.stairCompactCobblestone.blockID, this.func_35301_c(Block.stairCompactCobblestone.blockID, 3), 2, 0, -1, var3);
		}

		this.func_35309_a(var1, 0, 0, 6, 1, 5, var3);
		this.func_35309_a(var1, 0, 0, 6, 2, 5, var3);
		this.func_35309_a(var1, Block.torchWood.blockID, 0, 6, 3, 4, var3);
		this.func_35298_a(var1, var3, var2, 6, 1, 5, this.func_35301_c(Block.doorWood.blockID, 1));

		for(var6 = 0; var6 < 5; ++var6) {
			for(var7 = 0; var7 < 9; ++var7) {
				this.func_35314_b(var1, var7, 7, var6, var3);
				this.func_35303_b(var1, Block.cobblestone.blockID, 0, var7, -1, var6, var3);
			}
		}

		return true;
	}
}
