package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentStrongholdStraight extends ComponentStronghold {
	private final EnumDoor field_35050_a;
	private final boolean field_35048_b;
	private final boolean field_35049_c;

	public ComponentStrongholdStraight(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35025_h = var4;
		this.field_35050_a = this.func_35031_a(var2);
		this.field_35024_g = var3;
		this.field_35048_b = var2.nextInt(2) == 0;
		this.field_35049_c = var2.nextInt(2) == 0;
	}

	public void func_35004_a(StructureComponent var1, List var2, Random var3) {
		this.func_35028_a((ComponentStrongholdStairs2)var1, var2, var3, 1, 1);
		if(this.field_35048_b) {
			this.func_35032_b((ComponentStrongholdStairs2)var1, var2, var3, 1, 2);
		}

		if(this.field_35049_c) {
			this.func_35029_c((ComponentStrongholdStairs2)var1, var2, var3, 1, 2);
		}

	}

	public static ComponentStrongholdStraight func_35047_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35747_a(var2, var3, var4, -1, -1, 0, 5, 5, 7, var5);
		return func_35030_a(var7) && StructureComponent.func_35020_a(var0, var7) == null ? new ComponentStrongholdStraight(var6, var1, var7, var5) : null;
	}

	public boolean func_35023_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.func_35013_a(var1, var3)) {
			return false;
		} else {
			this.func_35022_a(var1, var3, 0, 0, 0, 4, 4, 6, true, var2, StructureStrongholdPieces.func_35852_b());
			this.func_35033_a(var1, var2, var3, this.field_35050_a, 1, 1, 0);
			this.func_35033_a(var1, var2, var3, EnumDoor.OPENING, 1, 1, 6);
			this.func_35014_a(var1, var3, var2, 0.1F, 1, 2, 1, Block.torchWood.blockID, 0);
			this.func_35014_a(var1, var3, var2, 0.1F, 3, 2, 1, Block.torchWood.blockID, 0);
			this.func_35014_a(var1, var3, var2, 0.1F, 1, 2, 5, Block.torchWood.blockID, 0);
			this.func_35014_a(var1, var3, var2, 0.1F, 3, 2, 5, Block.torchWood.blockID, 0);
			if(this.field_35048_b) {
				this.func_35011_a(var1, var3, 0, 1, 2, 0, 3, 4, 0, 0, false);
			}

			if(this.field_35049_c) {
				this.func_35011_a(var1, var3, 4, 1, 2, 4, 3, 4, 0, 0, false);
			}

			return true;
		}
	}
}
