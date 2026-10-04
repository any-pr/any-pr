package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentVillageTorch extends ComponentVillage {
	private int field_35383_a = -1;

	public ComponentVillageTorch(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35316_g = var3;
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
	}

	public static StructureBoundingBox func_35382_a(List var0, Random var1, int var2, int var3, int var4, int var5) {
		StructureBoundingBox var6 = StructureBoundingBox.func_35663_a(var2, var3, var4, 0, 0, 0, 3, 4, 2, var5);
		return StructureComponent.func_35312_a(var0, var6) != null ? null : var6;
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.field_35383_a < 0) {
			this.field_35383_a = this.func_35367_b(var1, var3);
			if(this.field_35383_a < 0) {
				return true;
			}

			this.field_35316_g.func_35670_a(0, this.field_35383_a - this.field_35316_g.field_35675_e + 4 - 1, 0);
		}

		this.func_35294_a(var1, var3, 0, 0, 0, 2, 3, 1, 0, 0, false);
		this.func_35309_a(var1, Block.fence.blockID, 0, 1, 0, 0, var3);
		this.func_35309_a(var1, Block.fence.blockID, 0, 1, 1, 0, var3);
		this.func_35309_a(var1, Block.fence.blockID, 0, 1, 2, 0, var3);
		this.func_35309_a(var1, Block.cloth.blockID, 15, 1, 3, 0, var3);
		this.func_35309_a(var1, Block.torchWood.blockID, 15, 0, 3, 0, var3);
		this.func_35309_a(var1, Block.torchWood.blockID, 15, 1, 3, 1, var3);
		this.func_35309_a(var1, Block.torchWood.blockID, 15, 2, 3, 0, var3);
		this.func_35309_a(var1, Block.torchWood.blockID, 15, 1, 3, -1, var3);
		return true;
	}
}
