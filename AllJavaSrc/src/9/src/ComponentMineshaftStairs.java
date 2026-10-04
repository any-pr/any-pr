package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentMineshaftStairs extends StructureComponent {
	public ComponentMineshaftStairs(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35025_h = var4;
		this.field_35024_g = var3;
	}

	public static StructureBoundingBox func_35027_a(List var0, Random var1, int var2, int var3, int var4, int var5) {
		StructureBoundingBox var6 = new StructureBoundingBox(var2, var3 - 5, var4, var2, var3 + 2, var4);
		switch(var5) {
		case 0:
			var6.field_35749_d = var2 + 2;
			var6.field_35748_f = var4 + 8;
			break;
		case 1:
			var6.field_35753_a = var2 - 8;
			var6.field_35748_f = var4 + 2;
			break;
		case 2:
			var6.field_35749_d = var2 + 2;
			var6.field_35752_c = var4 - 8;
			break;
		case 3:
			var6.field_35749_d = var2 + 8;
			var6.field_35748_f = var4 + 2;
		}

		return StructureComponent.func_35020_a(var0, var6) != null ? null : var6;
	}

	public void func_35004_a(StructureComponent var1, List var2, Random var3) {
		int var4 = this.func_35012_c();
		switch(this.field_35025_h) {
		case 0:
			StructureMineshaftPieces.func_35585_a(var1, var2, var3, this.field_35024_g.field_35753_a, this.field_35024_g.field_35751_b, this.field_35024_g.field_35748_f + 1, 0, var4);
			break;
		case 1:
			StructureMineshaftPieces.func_35585_a(var1, var2, var3, this.field_35024_g.field_35753_a - 1, this.field_35024_g.field_35751_b, this.field_35024_g.field_35752_c, 1, var4);
			break;
		case 2:
			StructureMineshaftPieces.func_35585_a(var1, var2, var3, this.field_35024_g.field_35753_a, this.field_35024_g.field_35751_b, this.field_35024_g.field_35752_c - 1, 2, var4);
			break;
		case 3:
			StructureMineshaftPieces.func_35585_a(var1, var2, var3, this.field_35024_g.field_35749_d + 1, this.field_35024_g.field_35751_b, this.field_35024_g.field_35752_c, 3, var4);
		}

	}

	public boolean func_35023_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.func_35013_a(var1, var3)) {
			return false;
		} else {
			this.func_35011_a(var1, var3, 0, 5, 0, 2, 7, 1, 0, 0, false);
			this.func_35011_a(var1, var3, 0, 0, 7, 2, 2, 8, 0, 0, false);

			for(int var4 = 0; var4 < 5; ++var4) {
				this.func_35011_a(var1, var3, 0, 5 - var4 - (var4 < 4 ? 1 : 0), 2 + var4, 2, 7 - var4, 2 + var4, 0, 0, false);
			}

			return true;
		}
	}
}
