package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentVillagePathGen extends ComponentVillageRoadPiece {
	private int field_35379_a;

	public ComponentVillagePathGen(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35316_g = var3;
		this.field_35379_a = Math.max(var3.func_35669_b(), var3.func_35665_d());
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
		boolean var4 = false;

		int var5;
		StructureComponent var6;
		for(var5 = var3.nextInt(5); var5 < this.field_35379_a - 8; var5 += 2 + var3.nextInt(5)) {
			var6 = this.func_35368_a((ComponentVillageStartPiece)var1, var2, var3, 0, var5);
			if(var6 != null) {
				var5 += Math.max(var6.field_35316_g.func_35669_b(), var6.field_35316_g.func_35665_d());
				var4 = true;
			}
		}

		for(var5 = var3.nextInt(5); var5 < this.field_35379_a - 8; var5 += 2 + var3.nextInt(5)) {
			var6 = this.func_35369_b((ComponentVillageStartPiece)var1, var2, var3, 0, var5);
			if(var6 != null) {
				var5 += Math.max(var6.field_35316_g.func_35669_b(), var6.field_35316_g.func_35665_d());
				var4 = true;
			}
		}

		if(var4 && var3.nextInt(3) > 0) {
			switch(this.field_35317_h) {
			case 0:
				StructureVillagePieces.func_35642_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35316_g.field_35678_a - 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35673_f - 2, 1, this.func_35305_c());
				break;
			case 1:
				StructureVillagePieces.func_35642_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35316_g.field_35678_a, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c - 1, 2, this.func_35305_c());
				break;
			case 2:
				StructureVillagePieces.func_35642_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35316_g.field_35678_a - 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c, 1, this.func_35305_c());
				break;
			case 3:
				StructureVillagePieces.func_35642_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35316_g.field_35674_d - 2, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c - 1, 2, this.func_35305_c());
			}
		}

		if(var4 && var3.nextInt(3) > 0) {
			switch(this.field_35317_h) {
			case 0:
				StructureVillagePieces.func_35642_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35316_g.field_35674_d + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35673_f - 2, 3, this.func_35305_c());
				break;
			case 1:
				StructureVillagePieces.func_35642_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35316_g.field_35678_a, this.field_35316_g.field_35676_b, this.field_35316_g.field_35673_f + 1, 0, this.func_35305_c());
				break;
			case 2:
				StructureVillagePieces.func_35642_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35316_g.field_35674_d + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c, 3, this.func_35305_c());
				break;
			case 3:
				StructureVillagePieces.func_35642_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35316_g.field_35674_d - 2, this.field_35316_g.field_35676_b, this.field_35316_g.field_35673_f + 1, 0, this.func_35305_c());
			}
		}

	}

	public static StructureBoundingBox func_35378_a(ComponentVillageStartPiece var0, List var1, Random var2, int var3, int var4, int var5, int var6) {
		for(int var7 = 7 * MathHelper.func_35476_a(var2, 3, 5); var7 >= 7; var7 -= 7) {
			StructureBoundingBox var8 = StructureBoundingBox.func_35663_a(var3, var4, var5, 0, 0, 0, 3, 3, var7, var6);
			if(StructureComponent.func_35312_a(var1, var8) == null) {
				return var8;
			}
		}

		return null;
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		for(int var4 = this.field_35316_g.field_35678_a; var4 <= this.field_35316_g.field_35674_d; ++var4) {
			for(int var5 = this.field_35316_g.field_35677_c; var5 <= this.field_35316_g.field_35673_f; ++var5) {
				if(var3.func_35667_b(var4, 64, var5)) {
					int var6 = var1.findTopSolidBlock(var4, var5) - 1;
					var1.setBlock(var4, var6, var5, Block.gravel.blockID);
				}
			}
		}

		return true;
	}
}
