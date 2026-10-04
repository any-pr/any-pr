package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentStrongholdLeftTurn extends ComponentStronghold {
	protected final EnumDoor field_35331_a;

	public ComponentStrongholdLeftTurn(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35331_a = this.func_35322_a(var2);
		this.field_35316_g = var3;
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
		if(this.field_35317_h != 2 && this.field_35317_h != 3) {
			this.func_35320_c((ComponentStrongholdStairs2)var1, var2, var3, 1, 1);
		} else {
			this.func_35321_b((ComponentStrongholdStairs2)var1, var2, var3, 1, 1);
		}

	}

	public static ComponentStrongholdLeftTurn func_35330_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, -1, -1, 0, 5, 5, 5, var5);
		return func_35319_a(var7) && StructureComponent.func_35312_a(var0, var7) == null ? new ComponentStrongholdLeftTurn(var6, var1, var7, var5) : null;
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.func_35295_a(var1, var3)) {
			return false;
		} else {
			this.func_35307_a(var1, var3, 0, 0, 0, 4, 4, 4, true, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35323_a(var1, var2, var3, this.field_35331_a, 1, 1, 0);
			if(this.field_35317_h != 2 && this.field_35317_h != 3) {
				this.func_35294_a(var1, var3, 4, 1, 1, 4, 3, 3, 0, 0, false);
			} else {
				this.func_35294_a(var1, var3, 0, 1, 1, 0, 3, 3, 0, 0, false);
			}

			return true;
		}
	}
}
