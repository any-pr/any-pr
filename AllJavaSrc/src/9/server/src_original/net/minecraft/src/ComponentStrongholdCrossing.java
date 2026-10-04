package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentStrongholdCrossing extends ComponentStronghold {
	protected final EnumDoor field_35355_a;
	private boolean field_35353_b;
	private boolean field_35354_c;
	private boolean field_35351_d;
	private boolean field_35352_e;

	public ComponentStrongholdCrossing(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35355_a = this.func_35322_a(var2);
		this.field_35316_g = var3;
		this.field_35353_b = var2.nextBoolean();
		this.field_35354_c = var2.nextBoolean();
		this.field_35351_d = var2.nextBoolean();
		this.field_35352_e = var2.nextBoolean();
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
		this.func_35324_a((ComponentStrongholdStairs2)var1, var2, var3, 5, 1);
		if(this.field_35353_b) {
			this.func_35321_b((ComponentStrongholdStairs2)var1, var2, var3, 3, 1);
		}

		if(this.field_35354_c) {
			this.func_35321_b((ComponentStrongholdStairs2)var1, var2, var3, 5, 7);
		}

		if(this.field_35351_d) {
			this.func_35320_c((ComponentStrongholdStairs2)var1, var2, var3, 3, 1);
		}

		if(this.field_35352_e) {
			this.func_35320_c((ComponentStrongholdStairs2)var1, var2, var3, 5, 7);
		}

	}

	public static ComponentStrongholdCrossing func_35350_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, -4, -3, 0, 10, 9, 11, var5);
		return func_35319_a(var7) && StructureComponent.func_35312_a(var0, var7) == null ? new ComponentStrongholdCrossing(var6, var1, var7, var5) : null;
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.func_35295_a(var1, var3)) {
			return false;
		} else {
			this.func_35307_a(var1, var3, 0, 0, 0, 9, 8, 10, true, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35323_a(var1, var2, var3, this.field_35355_a, 4, 3, 0);
			if(this.field_35353_b) {
				this.func_35294_a(var1, var3, 0, 3, 1, 0, 5, 3, 0, 0, false);
			}

			if(this.field_35351_d) {
				this.func_35294_a(var1, var3, 9, 3, 1, 9, 5, 3, 0, 0, false);
			}

			if(this.field_35354_c) {
				this.func_35294_a(var1, var3, 0, 5, 7, 0, 7, 9, 0, 0, false);
			}

			if(this.field_35352_e) {
				this.func_35294_a(var1, var3, 9, 5, 7, 9, 7, 9, 0, 0, false);
			}

			this.func_35294_a(var1, var3, 5, 1, 10, 7, 3, 10, 0, 0, false);
			this.func_35307_a(var1, var3, 1, 2, 1, 8, 2, 6, false, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35307_a(var1, var3, 4, 1, 5, 4, 4, 9, false, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35307_a(var1, var3, 8, 1, 5, 8, 4, 9, false, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35307_a(var1, var3, 1, 4, 7, 3, 4, 9, false, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35307_a(var1, var3, 1, 3, 5, 3, 3, 6, false, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35294_a(var1, var3, 1, 3, 4, 3, 3, 4, Block.stairSingle.blockID, Block.stairSingle.blockID, false);
			this.func_35294_a(var1, var3, 1, 4, 6, 3, 4, 6, Block.stairSingle.blockID, Block.stairSingle.blockID, false);
			this.func_35307_a(var1, var3, 5, 1, 7, 7, 1, 8, false, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35294_a(var1, var3, 5, 1, 9, 7, 1, 9, Block.stairSingle.blockID, Block.stairSingle.blockID, false);
			this.func_35294_a(var1, var3, 5, 2, 7, 7, 2, 7, Block.stairSingle.blockID, Block.stairSingle.blockID, false);
			this.func_35294_a(var1, var3, 4, 5, 7, 4, 5, 9, Block.stairSingle.blockID, Block.stairSingle.blockID, false);
			this.func_35294_a(var1, var3, 8, 5, 7, 8, 5, 9, Block.stairSingle.blockID, Block.stairSingle.blockID, false);
			this.func_35294_a(var1, var3, 5, 5, 7, 7, 5, 9, Block.stairDouble.blockID, Block.stairDouble.blockID, false);
			this.func_35309_a(var1, Block.torchWood.blockID, 0, 6, 5, 6, var3);
			return true;
		}
	}
}
