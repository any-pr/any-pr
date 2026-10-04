package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentStrongholdStairs extends ComponentStronghold {
	private final boolean field_35327_a;
	private final EnumDoor field_35326_b;

	public ComponentStrongholdStairs(int var1, Random var2, int var3, int var4) {
		super(var1);
		this.field_35327_a = true;
		this.field_35317_h = var2.nextInt(4);
		this.field_35326_b = EnumDoor.OPENING;
		switch(this.field_35317_h) {
		case 0:
		case 2:
			this.field_35316_g = new StructureBoundingBox(var3, 64, var4, var3 + 5 - 1, 74, var4 + 5 - 1);
			break;
		default:
			this.field_35316_g = new StructureBoundingBox(var3, 64, var4, var3 + 5 - 1, 74, var4 + 5 - 1);
		}

	}

	public ComponentStrongholdStairs(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35327_a = false;
		this.field_35317_h = var4;
		this.field_35326_b = this.func_35322_a(var2);
		this.field_35316_g = var3;
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
		this.func_35324_a((ComponentStrongholdStairs2)var1, var2, var3, 1, 1);
	}

	public static ComponentStrongholdStairs func_35325_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, -1, -7, 0, 5, 11, 5, var5);
		return func_35319_a(var7) && StructureComponent.func_35312_a(var0, var7) == null ? new ComponentStrongholdStairs(var6, var1, var7, var5) : null;
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.func_35295_a(var1, var3)) {
			return false;
		} else {
			if(this.field_35327_a) {
			}

			this.func_35307_a(var1, var3, 0, 0, 0, 4, 10, 4, true, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35323_a(var1, var2, var3, this.field_35326_b, 1, 7, 0);
			this.func_35323_a(var1, var2, var3, EnumDoor.OPENING, 1, 1, 4);
			this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 2, 6, 1, var3);
			this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 1, 5, 1, var3);
			this.func_35309_a(var1, Block.stairSingle.blockID, 0, 1, 6, 1, var3);
			this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 1, 5, 2, var3);
			this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 1, 4, 3, var3);
			this.func_35309_a(var1, Block.stairSingle.blockID, 0, 1, 5, 3, var3);
			this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 2, 4, 3, var3);
			this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 3, 3, 3, var3);
			this.func_35309_a(var1, Block.stairSingle.blockID, 0, 3, 4, 3, var3);
			this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 3, 3, 2, var3);
			this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 3, 2, 1, var3);
			this.func_35309_a(var1, Block.stairSingle.blockID, 0, 3, 3, 1, var3);
			this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 2, 2, 1, var3);
			this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 1, 1, 1, var3);
			this.func_35309_a(var1, Block.stairSingle.blockID, 0, 1, 2, 1, var3);
			this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 1, 1, 2, var3);
			this.func_35309_a(var1, Block.stairSingle.blockID, 0, 1, 1, 3, var3);
			return true;
		}
	}
}
