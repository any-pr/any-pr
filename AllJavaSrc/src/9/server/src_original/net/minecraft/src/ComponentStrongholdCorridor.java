package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentStrongholdCorridor extends ComponentStronghold {
	private final int field_35343_a;

	public ComponentStrongholdCorridor(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35316_g = var3;
		this.field_35343_a = var4 != 2 && var4 != 0 ? var3.func_35669_b() : var3.func_35665_d();
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
	}

	public static StructureBoundingBox func_35342_a(List var0, Random var1, int var2, int var3, int var4, int var5) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, -1, -1, 0, 5, 5, 4, var5);
		StructureComponent var8 = StructureComponent.func_35312_a(var0, var7);
		if(var8 == null) {
			return null;
		} else {
			if(var8.func_35313_b().field_35676_b == var7.field_35676_b) {
				for(int var9 = 3; var9 >= 1; --var9) {
					var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, -1, -1, 0, 5, 5, var9 - 1, var5);
					if(!var8.func_35313_b().func_35664_a(var7)) {
						return StructureBoundingBox.func_35663_a(var2, var3, var4, -1, -1, 0, 5, 5, var9, var5);
					}
				}
			}

			return null;
		}
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.func_35295_a(var1, var3)) {
			return false;
		} else {
			for(int var4 = 0; var4 < this.field_35343_a; ++var4) {
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 0, 0, var4, var3);
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 1, 0, var4, var3);
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 2, 0, var4, var3);
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 3, 0, var4, var3);
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 4, 0, var4, var3);

				for(int var5 = 1; var5 <= 3; ++var5) {
					this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 0, var5, var4, var3);
					this.func_35309_a(var1, 0, 0, 1, var5, var4, var3);
					this.func_35309_a(var1, 0, 0, 2, var5, var4, var3);
					this.func_35309_a(var1, 0, 0, 3, var5, var4, var3);
					this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 4, var5, var4, var3);
				}

				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 0, 4, var4, var3);
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 1, 4, var4, var3);
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 2, 4, var4, var3);
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 3, 4, var4, var3);
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 4, 4, var4, var3);
			}

			return true;
		}
	}
}
