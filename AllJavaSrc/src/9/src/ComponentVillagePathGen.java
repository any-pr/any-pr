package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentVillagePathGen extends ComponentVillageRoadPiece {
	private int field_35088_a;

	public ComponentVillagePathGen(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35025_h = var4;
		this.field_35024_g = var3;
		this.field_35088_a = Math.max(var3.func_35744_b(), var3.func_35739_d());
	}

	public void func_35004_a(StructureComponent var1, List var2, Random var3) {
		boolean var4 = false;

		int var5;
		StructureComponent var6;
		for(var5 = var3.nextInt(5); var5 < this.field_35088_a - 8; var5 += 2 + var3.nextInt(5)) {
			var6 = this.func_35077_a((ComponentVillageStartPiece)var1, var2, var3, 0, var5);
			if(var6 != null) {
				var5 += Math.max(var6.field_35024_g.func_35744_b(), var6.field_35024_g.func_35739_d());
				var4 = true;
			}
		}

		for(var5 = var3.nextInt(5); var5 < this.field_35088_a - 8; var5 += 2 + var3.nextInt(5)) {
			var6 = this.func_35076_b((ComponentVillageStartPiece)var1, var2, var3, 0, var5);
			if(var6 != null) {
				var5 += Math.max(var6.field_35024_g.func_35744_b(), var6.field_35024_g.func_35739_d());
				var4 = true;
			}
		}

		if(var4 && var3.nextInt(3) > 0) {
			switch(this.field_35025_h) {
			case 0:
				StructureVillagePieces.func_35701_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35024_g.field_35753_a - 1, this.field_35024_g.field_35751_b, this.field_35024_g.field_35748_f - 2, 1, this.func_35012_c());
				break;
			case 1:
				StructureVillagePieces.func_35701_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35024_g.field_35753_a, this.field_35024_g.field_35751_b, this.field_35024_g.field_35752_c - 1, 2, this.func_35012_c());
				break;
			case 2:
				StructureVillagePieces.func_35701_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35024_g.field_35753_a - 1, this.field_35024_g.field_35751_b, this.field_35024_g.field_35752_c, 1, this.func_35012_c());
				break;
			case 3:
				StructureVillagePieces.func_35701_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35024_g.field_35749_d - 2, this.field_35024_g.field_35751_b, this.field_35024_g.field_35752_c - 1, 2, this.func_35012_c());
			}
		}

		if(var4 && var3.nextInt(3) > 0) {
			switch(this.field_35025_h) {
			case 0:
				StructureVillagePieces.func_35701_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35024_g.field_35749_d + 1, this.field_35024_g.field_35751_b, this.field_35024_g.field_35748_f - 2, 3, this.func_35012_c());
				break;
			case 1:
				StructureVillagePieces.func_35701_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35024_g.field_35753_a, this.field_35024_g.field_35751_b, this.field_35024_g.field_35748_f + 1, 0, this.func_35012_c());
				break;
			case 2:
				StructureVillagePieces.func_35701_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35024_g.field_35749_d + 1, this.field_35024_g.field_35751_b, this.field_35024_g.field_35752_c, 3, this.func_35012_c());
				break;
			case 3:
				StructureVillagePieces.func_35701_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35024_g.field_35749_d - 2, this.field_35024_g.field_35751_b, this.field_35024_g.field_35748_f + 1, 0, this.func_35012_c());
			}
		}

	}

	public static StructureBoundingBox func_35087_a(ComponentVillageStartPiece var0, List var1, Random var2, int var3, int var4, int var5, int var6) {
		for(int var7 = 7 * MathHelper.func_35598_a(var2, 3, 5); var7 >= 7; var7 -= 7) {
			StructureBoundingBox var8 = StructureBoundingBox.func_35747_a(var3, var4, var5, 0, 0, 0, 3, 3, var7, var6);
			if(StructureComponent.func_35020_a(var1, var8) == null) {
				return var8;
			}
		}

		return null;
	}

	public boolean func_35023_a(World var1, Random var2, StructureBoundingBox var3) {
		for(int var4 = this.field_35024_g.field_35753_a; var4 <= this.field_35024_g.field_35749_d; ++var4) {
			for(int var5 = this.field_35024_g.field_35752_c; var5 <= this.field_35024_g.field_35748_f; ++var5) {
				if(var3.func_35742_b(var4, 64, var5)) {
					int var6 = var1.getTopSolidOrLiquidBlock(var4, var5) - 1;
					var1.setBlock(var4, var6, var5, Block.gravel.blockID);
				}
			}
		}

		return true;
	}
}
