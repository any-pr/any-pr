package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentVillageChurch extends ComponentVillage {
	private int field_35381_a = -1;

	public ComponentVillageChurch(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35316_g = var3;
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
	}

	public static ComponentVillageChurch func_35380_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, 0, 0, 0, 5, 12, 9, var5);
		return func_35366_a(var7) && StructureComponent.func_35312_a(var0, var7) == null ? new ComponentVillageChurch(var6, var1, var7, var5) : null;
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.field_35381_a < 0) {
			this.field_35381_a = this.func_35367_b(var1, var3);
			if(this.field_35381_a < 0) {
				return true;
			}

			this.field_35316_g.func_35670_a(0, this.field_35381_a - this.field_35316_g.field_35675_e + 12 - 1, 0);
		}

		this.func_35294_a(var1, var3, 1, 1, 1, 3, 3, 7, 0, 0, false);
		this.func_35294_a(var1, var3, 1, 5, 1, 3, 9, 3, 0, 0, false);
		this.func_35294_a(var1, var3, 1, 0, 0, 3, 0, 8, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 1, 1, 0, 3, 10, 0, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 0, 1, 1, 0, 10, 3, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 4, 1, 1, 4, 10, 3, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 0, 0, 4, 0, 4, 7, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 4, 0, 4, 4, 4, 7, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 1, 1, 8, 3, 4, 8, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 1, 5, 4, 3, 10, 4, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 1, 5, 5, 3, 5, 7, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 0, 9, 0, 4, 9, 4, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 0, 4, 0, 4, 4, 4, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 0, 11, 2, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 4, 11, 2, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 2, 11, 0, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 2, 11, 4, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 1, 1, 6, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 1, 1, 7, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 2, 1, 7, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 3, 1, 6, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 3, 1, 7, var3);
		this.func_35309_a(var1, Block.stairCompactCobblestone.blockID, this.func_35301_c(Block.stairCompactCobblestone.blockID, 3), 1, 1, 5, var3);
		this.func_35309_a(var1, Block.stairCompactCobblestone.blockID, this.func_35301_c(Block.stairCompactCobblestone.blockID, 3), 2, 1, 6, var3);
		this.func_35309_a(var1, Block.stairCompactCobblestone.blockID, this.func_35301_c(Block.stairCompactCobblestone.blockID, 3), 3, 1, 5, var3);
		this.func_35309_a(var1, Block.stairCompactCobblestone.blockID, this.func_35301_c(Block.stairCompactCobblestone.blockID, 1), 1, 2, 7, var3);
		this.func_35309_a(var1, Block.stairCompactCobblestone.blockID, this.func_35301_c(Block.stairCompactCobblestone.blockID, 0), 3, 2, 7, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 2, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 3, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 4, 2, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 4, 3, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 6, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 7, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 4, 6, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 4, 7, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 2, 6, 0, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 2, 7, 0, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 2, 6, 4, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 2, 7, 4, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 3, 6, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 4, 3, 6, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 2, 3, 8, var3);
		this.func_35309_a(var1, Block.torchWood.blockID, 0, 2, 4, 7, var3);
		this.func_35309_a(var1, Block.torchWood.blockID, 0, 1, 4, 6, var3);
		this.func_35309_a(var1, Block.torchWood.blockID, 0, 3, 4, 6, var3);
		this.func_35309_a(var1, Block.torchWood.blockID, 0, 2, 4, 5, var3);
		int var4 = this.func_35301_c(Block.ladder.blockID, 4);

		int var5;
		for(var5 = 1; var5 <= 9; ++var5) {
			this.func_35309_a(var1, Block.ladder.blockID, var4, 3, var5, 3, var3);
		}

		this.func_35309_a(var1, 0, 0, 2, 1, 0, var3);
		this.func_35309_a(var1, 0, 0, 2, 2, 0, var3);
		this.func_35298_a(var1, var3, var2, 2, 1, 0, this.func_35301_c(Block.doorWood.blockID, 1));
		if(this.func_35297_a(var1, 2, 0, -1, var3) == 0 && this.func_35297_a(var1, 2, -1, -1, var3) != 0) {
			this.func_35309_a(var1, Block.stairCompactCobblestone.blockID, this.func_35301_c(Block.stairCompactCobblestone.blockID, 3), 2, 0, -1, var3);
		}

		for(var5 = 0; var5 < 9; ++var5) {
			for(int var6 = 0; var6 < 5; ++var6) {
				this.func_35314_b(var1, var6, 12, var5, var3);
				this.func_35303_b(var1, Block.cobblestone.blockID, 0, var6, -1, var5, var3);
			}
		}

		return true;
	}
}
