package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentStrongholdRightTurn extends ComponentStrongholdLeftTurn {
	public ComponentStrongholdRightTurn(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1, var2, var3, var4);
	}

	public void func_35004_a(StructureComponent var1, List var2, Random var3) {
		if(this.field_35025_h != 2 && this.field_35025_h != 3) {
			this.func_35032_b((ComponentStrongholdStairs2)var1, var2, var3, 1, 1);
		} else {
			this.func_35029_c((ComponentStrongholdStairs2)var1, var2, var3, 1, 1);
		}

	}

	public boolean func_35023_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.func_35013_a(var1, var3)) {
			return false;
		} else {
			this.func_35022_a(var1, var3, 0, 0, 0, 4, 4, 4, true, var2, StructureStrongholdPieces.func_35852_b());
			this.func_35033_a(var1, var2, var3, this.field_35046_a, 1, 1, 0);
			if(this.field_35025_h != 2 && this.field_35025_h != 3) {
				this.func_35011_a(var1, var3, 0, 1, 1, 0, 3, 3, 0, 0, false);
			} else {
				this.func_35011_a(var1, var3, 4, 1, 1, 4, 3, 3, 0, 0, false);
			}

			return true;
		}
	}
}
