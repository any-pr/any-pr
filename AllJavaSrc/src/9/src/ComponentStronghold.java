package net.minecraft.src;

import java.util.List;
import java.util.Random;

abstract class ComponentStronghold extends StructureComponent {
	protected ComponentStronghold(int var1) {
		super(var1);
	}

	protected void func_35033_a(World var1, Random var2, StructureBoundingBox var3, EnumDoor var4, int var5, int var6, int var7) {
		switch(EnumDoorHelper.field_35619_a[var4.ordinal()]) {
		case 1:
		default:
			this.func_35011_a(var1, var3, var5, var6, var7, var5 + 3 - 1, var6 + 3 - 1, var7, 0, 0, false);
			break;
		case 2:
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5, var6, var7, var3);
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5, var6 + 1, var7, var3);
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5, var6 + 2, var7, var3);
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5 + 1, var6 + 2, var7, var3);
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5 + 2, var6 + 2, var7, var3);
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5 + 2, var6 + 1, var7, var3);
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5 + 2, var6, var7, var3);
			this.func_35018_a(var1, Block.doorWood.blockID, 0, var5 + 1, var6, var7, var3);
			this.func_35018_a(var1, Block.doorWood.blockID, 8, var5 + 1, var6 + 1, var7, var3);
			break;
		case 3:
			this.func_35018_a(var1, 0, 0, var5 + 1, var6, var7, var3);
			this.func_35018_a(var1, 0, 0, var5 + 1, var6 + 1, var7, var3);
			this.func_35018_a(var1, Block.field_35288_bq.blockID, 0, var5, var6, var7, var3);
			this.func_35018_a(var1, Block.field_35288_bq.blockID, 0, var5, var6 + 1, var7, var3);
			this.func_35018_a(var1, Block.field_35288_bq.blockID, 0, var5, var6 + 2, var7, var3);
			this.func_35018_a(var1, Block.field_35288_bq.blockID, 0, var5 + 1, var6 + 2, var7, var3);
			this.func_35018_a(var1, Block.field_35288_bq.blockID, 0, var5 + 2, var6 + 2, var7, var3);
			this.func_35018_a(var1, Block.field_35288_bq.blockID, 0, var5 + 2, var6 + 1, var7, var3);
			this.func_35018_a(var1, Block.field_35288_bq.blockID, 0, var5 + 2, var6, var7, var3);
			break;
		case 4:
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5, var6, var7, var3);
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5, var6 + 1, var7, var3);
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5, var6 + 2, var7, var3);
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5 + 1, var6 + 2, var7, var3);
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5 + 2, var6 + 2, var7, var3);
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5 + 2, var6 + 1, var7, var3);
			this.func_35018_a(var1, Block.field_35285_bn.blockID, 0, var5 + 2, var6, var7, var3);
			this.func_35018_a(var1, Block.doorSteel.blockID, 0, var5 + 1, var6, var7, var3);
			this.func_35018_a(var1, Block.doorSteel.blockID, 8, var5 + 1, var6 + 1, var7, var3);
			this.func_35018_a(var1, Block.button.blockID, this.func_35009_c(Block.button.blockID, 4), var5 + 2, var6 + 1, var7 + 1, var3);
			this.func_35018_a(var1, Block.button.blockID, this.func_35009_c(Block.button.blockID, 3), var5 + 2, var6 + 1, var7 - 1, var3);
		}

	}

	protected EnumDoor func_35031_a(Random var1) {
		int var2 = var1.nextInt(5);
		switch(var2) {
		case 0:
		case 1:
		default:
			return EnumDoor.OPENING;
		case 2:
			return EnumDoor.WOOD_DOOR;
		case 3:
			return EnumDoor.GRATES;
		case 4:
			return EnumDoor.IRON_DOOR;
		}
	}

	protected StructureComponent func_35028_a(ComponentStrongholdStairs2 var1, List var2, Random var3, int var4, int var5) {
		switch(this.field_35025_h) {
		case 0:
			return StructureStrongholdPieces.func_35850_a(var1, var2, var3, this.field_35024_g.field_35753_a + var4, this.field_35024_g.field_35751_b + var5, this.field_35024_g.field_35748_f + 1, this.field_35025_h, this.func_35012_c());
		case 1:
			return StructureStrongholdPieces.func_35850_a(var1, var2, var3, this.field_35024_g.field_35753_a - 1, this.field_35024_g.field_35751_b + var5, this.field_35024_g.field_35752_c + var4, this.field_35025_h, this.func_35012_c());
		case 2:
			return StructureStrongholdPieces.func_35850_a(var1, var2, var3, this.field_35024_g.field_35753_a + var4, this.field_35024_g.field_35751_b + var5, this.field_35024_g.field_35752_c - 1, this.field_35025_h, this.func_35012_c());
		case 3:
			return StructureStrongholdPieces.func_35850_a(var1, var2, var3, this.field_35024_g.field_35749_d + 1, this.field_35024_g.field_35751_b + var5, this.field_35024_g.field_35752_c + var4, this.field_35025_h, this.func_35012_c());
		default:
			return null;
		}
	}

	protected StructureComponent func_35032_b(ComponentStrongholdStairs2 var1, List var2, Random var3, int var4, int var5) {
		switch(this.field_35025_h) {
		case 0:
			return StructureStrongholdPieces.func_35850_a(var1, var2, var3, this.field_35024_g.field_35753_a - 1, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35752_c + var5, 1, this.func_35012_c());
		case 1:
			return StructureStrongholdPieces.func_35850_a(var1, var2, var3, this.field_35024_g.field_35753_a + var5, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35752_c - 1, 2, this.func_35012_c());
		case 2:
			return StructureStrongholdPieces.func_35850_a(var1, var2, var3, this.field_35024_g.field_35753_a - 1, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35752_c + var5, 1, this.func_35012_c());
		case 3:
			return StructureStrongholdPieces.func_35850_a(var1, var2, var3, this.field_35024_g.field_35753_a + var5, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35752_c - 1, 2, this.func_35012_c());
		default:
			return null;
		}
	}

	protected StructureComponent func_35029_c(ComponentStrongholdStairs2 var1, List var2, Random var3, int var4, int var5) {
		switch(this.field_35025_h) {
		case 0:
			return StructureStrongholdPieces.func_35850_a(var1, var2, var3, this.field_35024_g.field_35749_d + 1, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35752_c + var5, 3, this.func_35012_c());
		case 1:
			return StructureStrongholdPieces.func_35850_a(var1, var2, var3, this.field_35024_g.field_35753_a + var5, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35748_f + 1, 0, this.func_35012_c());
		case 2:
			return StructureStrongholdPieces.func_35850_a(var1, var2, var3, this.field_35024_g.field_35749_d + 1, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35752_c + var5, 3, this.func_35012_c());
		case 3:
			return StructureStrongholdPieces.func_35850_a(var1, var2, var3, this.field_35024_g.field_35753_a + var5, this.field_35024_g.field_35751_b + var4, this.field_35024_g.field_35748_f + 1, 0, this.func_35012_c());
		default:
			return null;
		}
	}

	protected static boolean func_35030_a(StructureBoundingBox var0) {
		return var0 != null && var0.field_35751_b > 10;
	}
}
