package net.minecraft.src;

import java.util.List;
import java.util.Random;

abstract class ComponentVillage extends StructureComponent {
	protected ComponentVillage(int var1) {
		super(var1);
	}

	protected StructureComponent func_35077_a(ComponentVillageStartPiece var1, List var2, Random var3, int var4, int var5) {
		switch(this.field_35025_h) {
		case 0:
			return StructureVillagePieces.func_35704_a(var1, var2, var3, this.field_35024_g.field_35753_a - 1, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35752_c + var5, 1, this.func_35012_c());
		case 1:
			return StructureVillagePieces.func_35704_a(var1, var2, var3, this.field_35024_g.field_35753_a + var5, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35752_c - 1, 2, this.func_35012_c());
		case 2:
			return StructureVillagePieces.func_35704_a(var1, var2, var3, this.field_35024_g.field_35753_a - 1, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35752_c + var5, 1, this.func_35012_c());
		case 3:
			return StructureVillagePieces.func_35704_a(var1, var2, var3, this.field_35024_g.field_35753_a + var5, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35752_c - 1, 2, this.func_35012_c());
		default:
			return null;
		}
	}

	protected StructureComponent func_35076_b(ComponentVillageStartPiece var1, List var2, Random var3, int var4, int var5) {
		switch(this.field_35025_h) {
		case 0:
			return StructureVillagePieces.func_35704_a(var1, var2, var3, this.field_35024_g.field_35749_d + 1, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35752_c + var5, 3, this.func_35012_c());
		case 1:
			return StructureVillagePieces.func_35704_a(var1, var2, var3, this.field_35024_g.field_35753_a + var5, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35748_f + 1, 0, this.func_35012_c());
		case 2:
			return StructureVillagePieces.func_35704_a(var1, var2, var3, this.field_35024_g.field_35749_d + 1, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35752_c + var5, 3, this.func_35012_c());
		case 3:
			return StructureVillagePieces.func_35704_a(var1, var2, var3, this.field_35024_g.field_35753_a + var5, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35748_f + 1, 0, this.func_35012_c());
		default:
			return null;
		}
	}

	protected int func_35075_b(World var1, StructureBoundingBox var2) {
		int var3 = 0;
		int var4 = 0;

		for(int var5 = this.field_35024_g.field_35752_c; var5 <= this.field_35024_g.field_35748_f; ++var5) {
			for(int var6 = this.field_35024_g.field_35753_a; var6 <= this.field_35024_g.field_35749_d; ++var6) {
				if(var2.func_35742_b(var6, 64, var5)) {
					int var10001 = var1.getTopSolidOrLiquidBlock(var6, var5);
					var1.getClass();
					var3 += Math.max(var10001, 63);
					++var4;
				}
			}
		}

		if(var4 == 0) {
			return -1;
		} else {
			return var3 / var4;
		}
	}

	protected static boolean func_35074_a(StructureBoundingBox var0) {
		return var0 != null && var0.field_35751_b > 10;
	}
}
