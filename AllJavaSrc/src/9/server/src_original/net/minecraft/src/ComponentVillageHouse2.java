package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentVillageHouse2 extends ComponentVillage {
	private int field_35377_a = -1;

	public ComponentVillageHouse2(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35316_g = var3;
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
	}

	public static ComponentVillageHouse2 func_35376_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, 0, 0, 0, 10, 6, 7, var5);
		return func_35366_a(var7) && StructureComponent.func_35312_a(var0, var7) == null ? new ComponentVillageHouse2(var6, var1, var7, var5) : null;
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.field_35377_a < 0) {
			this.field_35377_a = this.func_35367_b(var1, var3);
			if(this.field_35377_a < 0) {
				return true;
			}

			this.field_35316_g.func_35670_a(0, this.field_35377_a - this.field_35316_g.field_35675_e + 6 - 1, 0);
		}

		this.func_35294_a(var1, var3, 0, 1, 0, 9, 4, 6, 0, 0, false);
		this.func_35294_a(var1, var3, 0, 0, 0, 9, 0, 6, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 0, 4, 0, 9, 4, 6, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 0, 5, 0, 9, 5, 6, Block.stairSingle.blockID, Block.stairSingle.blockID, false);
		this.func_35294_a(var1, var3, 1, 5, 1, 8, 5, 5, 0, 0, false);
		this.func_35294_a(var1, var3, 1, 1, 0, 2, 3, 0, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 0, 1, 0, 0, 4, 0, Block.wood.blockID, Block.wood.blockID, false);
		this.func_35294_a(var1, var3, 3, 1, 0, 3, 4, 0, Block.wood.blockID, Block.wood.blockID, false);
		this.func_35294_a(var1, var3, 0, 1, 6, 0, 4, 6, Block.wood.blockID, Block.wood.blockID, false);
		this.func_35309_a(var1, Block.planks.blockID, 0, 3, 3, 1, var3);
		this.func_35294_a(var1, var3, 3, 1, 2, 3, 3, 2, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 4, 1, 3, 5, 3, 3, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 0, 1, 1, 0, 3, 5, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 1, 1, 6, 5, 3, 6, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 5, 1, 0, 5, 3, 0, Block.fence.blockID, Block.fence.blockID, false);
		this.func_35294_a(var1, var3, 9, 1, 0, 9, 3, 0, Block.fence.blockID, Block.fence.blockID, false);
		this.func_35294_a(var1, var3, 6, 1, 4, 9, 4, 6, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35309_a(var1, Block.lavaMoving.blockID, 0, 7, 1, 5, var3);
		this.func_35309_a(var1, Block.lavaMoving.blockID, 0, 8, 1, 5, var3);
		this.func_35309_a(var1, Block.field_35055_bq.blockID, 0, 9, 2, 5, var3);
		this.func_35309_a(var1, Block.field_35055_bq.blockID, 0, 9, 2, 4, var3);
		this.func_35294_a(var1, var3, 7, 2, 4, 8, 2, 5, 0, 0, false);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 6, 1, 3, var3);
		this.func_35309_a(var1, Block.stoneOvenIdle.blockID, 0, 6, 2, 3, var3);
		this.func_35309_a(var1, Block.stoneOvenIdle.blockID, 0, 6, 3, 3, var3);
		this.func_35309_a(var1, Block.stairDouble.blockID, 0, 8, 1, 1, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 2, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 2, 4, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 2, 2, 6, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 4, 2, 6, var3);
		this.func_35309_a(var1, Block.fence.blockID, 0, 2, 1, 4, var3);
		this.func_35309_a(var1, Block.pressurePlatePlanks.blockID, 0, 2, 2, 4, var3);
		this.func_35309_a(var1, Block.planks.blockID, 0, 1, 1, 5, var3);
		this.func_35309_a(var1, Block.stairCompactPlanks.blockID, this.func_35301_c(Block.stairCompactPlanks.blockID, 3), 2, 1, 5, var3);
		this.func_35309_a(var1, Block.stairCompactPlanks.blockID, this.func_35301_c(Block.stairCompactPlanks.blockID, 1), 1, 1, 4, var3);

		int var4;
		for(var4 = 6; var4 <= 8; ++var4) {
			if(this.func_35297_a(var1, var4, 0, -1, var3) == 0 && this.func_35297_a(var1, var4, -1, -1, var3) != 0) {
				this.func_35309_a(var1, Block.stairCompactCobblestone.blockID, this.func_35301_c(Block.stairCompactCobblestone.blockID, 3), var4, 0, -1, var3);
			}
		}

		for(var4 = 0; var4 < 7; ++var4) {
			for(int var5 = 0; var5 < 10; ++var5) {
				this.func_35314_b(var1, var5, 6, var4, var3);
				this.func_35303_b(var1, Block.cobblestone.blockID, 0, var5, -1, var4, var3);
			}
		}

		return true;
	}
}
