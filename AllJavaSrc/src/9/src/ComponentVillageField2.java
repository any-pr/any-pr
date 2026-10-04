package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentVillageField2 extends ComponentVillage {
	private int field_35090_a = -1;

	public ComponentVillageField2(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35025_h = var4;
		this.field_35024_g = var3;
	}

	public void func_35004_a(StructureComponent var1, List var2, Random var3) {
	}

	public static ComponentVillageField2 func_35089_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35747_a(var2, var3, var4, 0, 0, 0, 7, 4, 9, var5);
		return func_35074_a(var7) && StructureComponent.func_35020_a(var0, var7) == null ? new ComponentVillageField2(var6, var1, var7, var5) : null;
	}

	public boolean func_35023_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.field_35090_a < 0) {
			this.field_35090_a = this.func_35075_b(var1, var3);
			if(this.field_35090_a < 0) {
				return true;
			}

			this.field_35024_g.func_35745_a(0, this.field_35090_a - this.field_35024_g.field_35750_e + 4 - 1, 0);
		}

		this.func_35011_a(var1, var3, 0, 1, 0, 6, 4, 8, 0, 0, false);
		this.func_35011_a(var1, var3, 1, 0, 1, 2, 0, 7, Block.tilledField.blockID, Block.tilledField.blockID, false);
		this.func_35011_a(var1, var3, 4, 0, 1, 5, 0, 7, Block.tilledField.blockID, Block.tilledField.blockID, false);
		this.func_35011_a(var1, var3, 0, 0, 0, 0, 0, 8, Block.wood.blockID, Block.wood.blockID, false);
		this.func_35011_a(var1, var3, 6, 0, 0, 6, 0, 8, Block.wood.blockID, Block.wood.blockID, false);
		this.func_35011_a(var1, var3, 1, 0, 0, 5, 0, 0, Block.wood.blockID, Block.wood.blockID, false);
		this.func_35011_a(var1, var3, 1, 0, 8, 5, 0, 8, Block.wood.blockID, Block.wood.blockID, false);
		this.func_35011_a(var1, var3, 3, 0, 1, 3, 0, 7, Block.waterMoving.blockID, Block.waterMoving.blockID, false);

		int var4;
		for(var4 = 1; var4 <= 7; ++var4) {
			this.func_35018_a(var1, Block.crops.blockID, MathHelper.func_35598_a(var2, 2, 7), 1, 1, var4, var3);
			this.func_35018_a(var1, Block.crops.blockID, MathHelper.func_35598_a(var2, 2, 7), 2, 1, var4, var3);
			this.func_35018_a(var1, Block.crops.blockID, MathHelper.func_35598_a(var2, 2, 7), 4, 1, var4, var3);
			this.func_35018_a(var1, Block.crops.blockID, MathHelper.func_35598_a(var2, 2, 7), 5, 1, var4, var3);
		}

		for(var4 = 0; var4 < 9; ++var4) {
			for(int var5 = 0; var5 < 7; ++var5) {
				this.func_35016_b(var1, var5, 4, var4, var3);
				this.func_35005_b(var1, Block.dirt.blockID, 0, var5, -1, var4, var3);
			}
		}

		return true;
	}
}
