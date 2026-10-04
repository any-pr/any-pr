package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentStrongholdPrison extends ComponentStronghold {
	protected final EnumDoor field_35333_a;

	public ComponentStrongholdPrison(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35333_a = this.func_35322_a(var2);
		this.field_35316_g = var3;
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
		this.func_35324_a((ComponentStrongholdStairs2)var1, var2, var3, 1, 1);
	}

	public static ComponentStrongholdPrison func_35332_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, -1, -1, 0, 9, 5, 11, var5);
		return func_35319_a(var7) && StructureComponent.func_35312_a(var0, var7) == null ? new ComponentStrongholdPrison(var6, var1, var7, var5) : null;
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.func_35295_a(var1, var3)) {
			return false;
		} else {
			this.func_35307_a(var1, var3, 0, 0, 0, 8, 4, 10, true, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35323_a(var1, var2, var3, this.field_35333_a, 1, 1, 0);
			this.func_35294_a(var1, var3, 1, 1, 10, 3, 3, 10, 0, 0, false);
			this.func_35307_a(var1, var3, 4, 1, 1, 4, 3, 1, false, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35307_a(var1, var3, 4, 1, 3, 4, 3, 3, false, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35307_a(var1, var3, 4, 1, 7, 4, 3, 7, false, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35307_a(var1, var3, 4, 1, 9, 4, 3, 9, false, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35294_a(var1, var3, 4, 1, 4, 4, 3, 6, Block.field_35055_bq.blockID, Block.field_35055_bq.blockID, false);
			this.func_35294_a(var1, var3, 5, 1, 5, 7, 3, 5, Block.field_35055_bq.blockID, Block.field_35055_bq.blockID, false);
			this.func_35309_a(var1, Block.field_35055_bq.blockID, 0, 4, 3, 2, var3);
			this.func_35309_a(var1, Block.field_35055_bq.blockID, 0, 4, 3, 8, var3);
			this.func_35309_a(var1, Block.doorSteel.blockID, this.func_35301_c(Block.doorSteel.blockID, 3), 4, 1, 2, var3);
			this.func_35309_a(var1, Block.doorSteel.blockID, this.func_35301_c(Block.doorSteel.blockID, 3) + 8, 4, 2, 2, var3);
			this.func_35309_a(var1, Block.doorSteel.blockID, this.func_35301_c(Block.doorSteel.blockID, 3), 4, 1, 8, var3);
			this.func_35309_a(var1, Block.doorSteel.blockID, this.func_35301_c(Block.doorSteel.blockID, 3) + 8, 4, 2, 8, var3);
			return true;
		}
	}
}
