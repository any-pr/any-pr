package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentStrongholdStairsStraight extends ComponentStronghold {
	private final EnumDoor field_35054_a;

	public ComponentStrongholdStairsStraight(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35025_h = var4;
		this.field_35054_a = this.func_35031_a(var2);
		this.field_35024_g = var3;
	}

	public void func_35004_a(StructureComponent var1, List var2, Random var3) {
		this.func_35028_a((ComponentStrongholdStairs2)var1, var2, var3, 1, 1);
	}

	public static ComponentStrongholdStairsStraight func_35053_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35747_a(var2, var3, var4, -1, -7, 0, 5, 11, 8, var5);
		return func_35030_a(var7) && StructureComponent.func_35020_a(var0, var7) == null ? new ComponentStrongholdStairsStraight(var6, var1, var7, var5) : null;
	}

	public boolean func_35023_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.func_35013_a(var1, var3)) {
			return false;
		} else {
			this.func_35022_a(var1, var3, 0, 0, 0, 4, 10, 7, true, var2, StructureStrongholdPieces.func_35852_b());
			this.func_35033_a(var1, var2, var3, this.field_35054_a, 1, 7, 0);
			this.func_35033_a(var1, var2, var3, EnumDoor.OPENING, 1, 1, 7);
			int var4 = this.func_35009_c(Block.stairCompactCobblestone.blockID, 2);

			for(int var5 = 0; var5 < 6; ++var5) {
				this.func_35018_a(var1, Block.stairCompactCobblestone.blockID, var4, 1, 6 - var5, 1 + var5, var3);
				this.func_35018_a(var1, Block.stairCompactCobblestone.blockID, var4, 2, 6 - var5, 1 + var5, var3);
				this.func_35018_a(var1, Block.stairCompactCobblestone.blockID, var4, 3, 6 - var5, 1 + var5, var3);
				if(var5 < 5) {
					this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, 1, 5 - var5, 1 + var5, var3);
					this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, 2, 5 - var5, 1 + var5, var3);
					this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, 3, 5 - var5, 1 + var5, var3);
				}
			}

			return true;
		}
	}
}
