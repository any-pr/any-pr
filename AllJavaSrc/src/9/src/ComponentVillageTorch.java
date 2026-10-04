package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentVillageTorch extends ComponentVillage {
	private int field_35100_a = -1;

	public ComponentVillageTorch(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35025_h = var4;
		this.field_35024_g = var3;
	}

	public void func_35004_a(StructureComponent var1, List var2, Random var3) {
	}

	public static StructureBoundingBox func_35099_a(List var0, Random var1, int var2, int var3, int var4, int var5) {
		StructureBoundingBox var6 = StructureBoundingBox.func_35747_a(var2, var3, var4, 0, 0, 0, 3, 4, 2, var5);
		return StructureComponent.func_35020_a(var0, var6) != null ? null : var6;
	}

	public boolean func_35023_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.field_35100_a < 0) {
			this.field_35100_a = this.func_35075_b(var1, var3);
			if(this.field_35100_a < 0) {
				return true;
			}

			this.field_35024_g.func_35745_a(0, this.field_35100_a - this.field_35024_g.field_35750_e + 4 - 1, 0);
		}

		this.func_35011_a(var1, var3, 0, 0, 0, 2, 3, 1, 0, 0, false);
		this.func_35018_a(var1, Block.fence.blockID, 0, 1, 0, 0, var3);
		this.func_35018_a(var1, Block.fence.blockID, 0, 1, 1, 0, var3);
		this.func_35018_a(var1, Block.fence.blockID, 0, 1, 2, 0, var3);
		this.func_35018_a(var1, Block.cloth.blockID, 15, 1, 3, 0, var3);
		this.func_35018_a(var1, Block.torchWood.blockID, 15, 0, 3, 0, var3);
		this.func_35018_a(var1, Block.torchWood.blockID, 15, 1, 3, 1, var3);
		this.func_35018_a(var1, Block.torchWood.blockID, 15, 2, 3, 0, var3);
		this.func_35018_a(var1, Block.torchWood.blockID, 15, 1, 3, -1, var3);
		return true;
	}
}
