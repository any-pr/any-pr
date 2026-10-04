package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentVillageWoodHut extends ComponentVillage {
	private int field_35094_a = -1;
	private final boolean field_35092_b;
	private final int field_35093_c;

	public ComponentVillageWoodHut(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35025_h = var4;
		this.field_35024_g = var3;
		this.field_35092_b = var2.nextBoolean();
		this.field_35093_c = var2.nextInt(3);
	}

	public void func_35004_a(StructureComponent var1, List var2, Random var3) {
	}

	public static ComponentVillageWoodHut func_35091_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35747_a(var2, var3, var4, 0, 0, 0, 4, 6, 5, var5);
		return func_35074_a(var7) && StructureComponent.func_35020_a(var0, var7) == null ? new ComponentVillageWoodHut(var6, var1, var7, var5) : null;
	}

	public boolean func_35023_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.field_35094_a < 0) {
			this.field_35094_a = this.func_35075_b(var1, var3);
			if(this.field_35094_a < 0) {
				return true;
			}

			this.field_35024_g.func_35745_a(0, this.field_35094_a - this.field_35024_g.field_35750_e + 6 - 1, 0);
		}

		this.func_35011_a(var1, var3, 1, 1, 1, 3, 5, 4, 0, 0, false);
		this.func_35011_a(var1, var3, 0, 0, 0, 3, 0, 4, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35011_a(var1, var3, 1, 0, 1, 2, 0, 3, Block.dirt.blockID, Block.dirt.blockID, false);
		if(this.field_35092_b) {
			this.func_35011_a(var1, var3, 1, 4, 1, 2, 4, 3, Block.wood.blockID, Block.wood.blockID, false);
		} else {
			this.func_35011_a(var1, var3, 1, 5, 1, 2, 5, 3, Block.wood.blockID, Block.wood.blockID, false);
		}

		this.func_35018_a(var1, Block.wood.blockID, 0, 1, 4, 0, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 2, 4, 0, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 1, 4, 4, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 2, 4, 4, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 0, 4, 1, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 0, 4, 2, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 0, 4, 3, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 3, 4, 1, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 3, 4, 2, var3);
		this.func_35018_a(var1, Block.wood.blockID, 0, 3, 4, 3, var3);
		this.func_35011_a(var1, var3, 0, 1, 0, 0, 3, 0, Block.wood.blockID, Block.wood.blockID, false);
		this.func_35011_a(var1, var3, 3, 1, 0, 3, 3, 0, Block.wood.blockID, Block.wood.blockID, false);
		this.func_35011_a(var1, var3, 0, 1, 4, 0, 3, 4, Block.wood.blockID, Block.wood.blockID, false);
		this.func_35011_a(var1, var3, 3, 1, 4, 3, 3, 4, Block.wood.blockID, Block.wood.blockID, false);
		this.func_35011_a(var1, var3, 0, 1, 1, 0, 3, 3, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35011_a(var1, var3, 3, 1, 1, 3, 3, 3, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35011_a(var1, var3, 1, 1, 0, 2, 3, 0, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35011_a(var1, var3, 1, 1, 4, 2, 3, 4, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35018_a(var1, Block.field_35282_br.blockID, 0, 0, 2, 2, var3);
		this.func_35018_a(var1, Block.field_35282_br.blockID, 0, 3, 2, 2, var3);
		if(this.field_35093_c > 0) {
			this.func_35018_a(var1, Block.fence.blockID, 0, this.field_35093_c, 1, 3, var3);
			this.func_35018_a(var1, Block.pressurePlatePlanks.blockID, 0, this.field_35093_c, 2, 3, var3);
		}

		this.func_35018_a(var1, 0, 0, 1, 1, 0, var3);
		this.func_35018_a(var1, 0, 0, 1, 2, 0, var3);
		this.func_35002_a(var1, var3, var2, 1, 1, 0, this.func_35009_c(Block.doorWood.blockID, 1));
		if(this.func_35007_a(var1, 1, 0, -1, var3) == 0 && this.func_35007_a(var1, 1, -1, -1, var3) != 0) {
			this.func_35018_a(var1, Block.stairCompactCobblestone.blockID, this.func_35009_c(Block.stairCompactCobblestone.blockID, 3), 1, 0, -1, var3);
		}

		for(int var4 = 0; var4 < 5; ++var4) {
			for(int var5 = 0; var5 < 4; ++var5) {
				this.func_35016_b(var1, var5, 6, var4, var3);
				this.func_35005_b(var1, Block.cobblestone.blockID, 0, var5, -1, var4, var3);
			}
		}

		return true;
	}
}
