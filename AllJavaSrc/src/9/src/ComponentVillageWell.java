package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentVillageWell extends ComponentVillage {
	private final boolean field_35104_a = true;
	private int field_35103_b = -1;

	public ComponentVillageWell(int var1, Random var2, int var3, int var4) {
		super(var1);
		this.field_35025_h = var2.nextInt(4);
		switch(this.field_35025_h) {
		case 0:
		case 2:
			this.field_35024_g = new StructureBoundingBox(var3, 64, var4, var3 + 6 - 1, 78, var4 + 6 - 1);
			break;
		default:
			this.field_35024_g = new StructureBoundingBox(var3, 64, var4, var3 + 6 - 1, 78, var4 + 6 - 1);
		}

	}

	public void func_35004_a(StructureComponent var1, List var2, Random var3) {
		StructureVillagePieces.func_35701_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35024_g.field_35753_a - 1, this.field_35024_g.field_35750_e - 4, this.field_35024_g.field_35752_c + 1, 1, this.func_35012_c());
		StructureVillagePieces.func_35701_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35024_g.field_35749_d + 1, this.field_35024_g.field_35750_e - 4, this.field_35024_g.field_35752_c + 1, 3, this.func_35012_c());
		StructureVillagePieces.func_35701_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35024_g.field_35753_a + 1, this.field_35024_g.field_35750_e - 4, this.field_35024_g.field_35752_c - 1, 2, this.func_35012_c());
		StructureVillagePieces.func_35701_b((ComponentVillageStartPiece)var1, var2, var3, this.field_35024_g.field_35753_a + 1, this.field_35024_g.field_35750_e - 4, this.field_35024_g.field_35748_f + 1, 0, this.func_35012_c());
	}

	public boolean func_35023_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.field_35103_b < 0) {
			this.field_35103_b = this.func_35075_b(var1, var3);
			if(this.field_35103_b < 0) {
				return true;
			}

			this.field_35024_g.func_35745_a(0, this.field_35103_b - this.field_35024_g.field_35750_e + 3, 0);
		}

		if(this.field_35104_a) {
		}

		this.func_35011_a(var1, var3, 1, 0, 1, 4, 12, 4, Block.cobblestone.blockID, Block.waterMoving.blockID, false);
		this.func_35018_a(var1, 0, 0, 2, 12, 2, var3);
		this.func_35018_a(var1, 0, 0, 3, 12, 2, var3);
		this.func_35018_a(var1, 0, 0, 2, 12, 3, var3);
		this.func_35018_a(var1, 0, 0, 3, 12, 3, var3);
		this.func_35018_a(var1, Block.fence.blockID, 0, 1, 13, 1, var3);
		this.func_35018_a(var1, Block.fence.blockID, 0, 1, 14, 1, var3);
		this.func_35018_a(var1, Block.fence.blockID, 0, 4, 13, 1, var3);
		this.func_35018_a(var1, Block.fence.blockID, 0, 4, 14, 1, var3);
		this.func_35018_a(var1, Block.fence.blockID, 0, 1, 13, 4, var3);
		this.func_35018_a(var1, Block.fence.blockID, 0, 1, 14, 4, var3);
		this.func_35018_a(var1, Block.fence.blockID, 0, 4, 13, 4, var3);
		this.func_35018_a(var1, Block.fence.blockID, 0, 4, 14, 4, var3);
		this.func_35011_a(var1, var3, 1, 15, 1, 4, 15, 4, Block.cobblestone.blockID, Block.cobblestone.blockID, false);

		for(int var4 = 0; var4 <= 5; ++var4) {
			for(int var5 = 0; var5 <= 5; ++var5) {
				if(var5 == 0 || var5 == 5 || var4 == 0 || var4 == 5) {
					this.func_35018_a(var1, Block.gravel.blockID, 0, var5, 11, var4, var3);
					this.func_35016_b(var1, var5, 12, var4, var3);
				}
			}
		}

		return true;
	}
}
