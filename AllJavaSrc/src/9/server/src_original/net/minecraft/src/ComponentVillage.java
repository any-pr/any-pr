package net.minecraft.src;

import java.util.List;
import java.util.Random;

abstract class ComponentVillage extends StructureComponent {
	protected ComponentVillage(int var1) {
		super(var1);
	}

	protected StructureComponent func_35368_a(ComponentVillageStartPiece var1, List var2, Random var3, int var4, int var5) {
		switch(this.field_35317_h) {
		case 0:
			return StructureVillagePieces.func_35640_a(var1, var2, var3, this.field_35316_g.field_35678_a - 1, this.field_35316_g.field_35676_b + var4, this.field_35316_g.field_35677_c + var5, 1, this.func_35305_c());
		case 1:
			return StructureVillagePieces.func_35640_a(var1, var2, var3, this.field_35316_g.field_35678_a + var5, this.field_35316_g.field_35676_b + var4, this.field_35316_g.field_35677_c - 1, 2, this.func_35305_c());
		case 2:
			return StructureVillagePieces.func_35640_a(var1, var2, var3, this.field_35316_g.field_35678_a - 1, this.field_35316_g.field_35676_b + var4, this.field_35316_g.field_35677_c + var5, 1, this.func_35305_c());
		case 3:
			return StructureVillagePieces.func_35640_a(var1, var2, var3, this.field_35316_g.field_35678_a + var5, this.field_35316_g.field_35676_b + var4, this.field_35316_g.field_35677_c - 1, 2, this.func_35305_c());
		default:
			return null;
		}
	}

	protected StructureComponent func_35369_b(ComponentVillageStartPiece var1, List var2, Random var3, int var4, int var5) {
		switch(this.field_35317_h) {
		case 0:
			return StructureVillagePieces.func_35640_a(var1, var2, var3, this.field_35316_g.field_35674_d + 1, this.field_35316_g.field_35676_b + var4, this.field_35316_g.field_35677_c + var5, 3, this.func_35305_c());
		case 1:
			return StructureVillagePieces.func_35640_a(var1, var2, var3, this.field_35316_g.field_35678_a + var5, this.field_35316_g.field_35676_b + var4, this.field_35316_g.field_35673_f + 1, 0, this.func_35305_c());
		case 2:
			return StructureVillagePieces.func_35640_a(var1, var2, var3, this.field_35316_g.field_35674_d + 1, this.field_35316_g.field_35676_b + var4, this.field_35316_g.field_35677_c + var5, 3, this.func_35305_c());
		case 3:
			return StructureVillagePieces.func_35640_a(var1, var2, var3, this.field_35316_g.field_35678_a + var5, this.field_35316_g.field_35676_b + var4, this.field_35316_g.field_35673_f + 1, 0, this.func_35305_c());
		default:
			return null;
		}
	}

	protected int func_35367_b(World var1, StructureBoundingBox var2) {
		int var3 = 0;
		int var4 = 0;

		for(int var5 = this.field_35316_g.field_35677_c; var5 <= this.field_35316_g.field_35673_f; ++var5) {
			for(int var6 = this.field_35316_g.field_35678_a; var6 <= this.field_35316_g.field_35674_d; ++var6) {
				if(var2.func_35667_b(var6, 64, var5)) {
					int var10001 = var1.findTopSolidBlock(var6, var5);
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

	protected static boolean func_35366_a(StructureBoundingBox var0) {
		return var0 != null && var0.field_35676_b > 10;
	}
}
