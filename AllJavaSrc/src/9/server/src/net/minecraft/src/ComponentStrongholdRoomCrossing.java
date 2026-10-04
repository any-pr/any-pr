package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentStrongholdRoomCrossing extends ComponentStronghold {
	private static final StructurePieceTreasure[] field_35348_c = new StructurePieceTreasure[]{new StructurePieceTreasure(Item.ingotIron.shiftedIndex, 0, 1, 5, 10), new StructurePieceTreasure(Item.ingotGold.shiftedIndex, 0, 1, 3, 5), new StructurePieceTreasure(Item.redstone.shiftedIndex, 0, 4, 9, 5), new StructurePieceTreasure(Item.coal.shiftedIndex, 0, 3, 8, 10), new StructurePieceTreasure(Item.bread.shiftedIndex, 0, 1, 3, 15), new StructurePieceTreasure(Item.appleRed.shiftedIndex, 0, 1, 3, 15), new StructurePieceTreasure(Item.pickaxeSteel.shiftedIndex, 0, 1, 1, 1)};
	protected final EnumDoor field_35349_a;
	protected final int field_35347_b;

	public ComponentStrongholdRoomCrossing(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35349_a = this.func_35322_a(var2);
		this.field_35316_g = var3;
		this.field_35347_b = var2.nextInt(5);
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
		this.func_35324_a((ComponentStrongholdStairs2)var1, var2, var3, 4, 1);
		this.func_35321_b((ComponentStrongholdStairs2)var1, var2, var3, 1, 4);
		this.func_35320_c((ComponentStrongholdStairs2)var1, var2, var3, 1, 4);
	}

	public static ComponentStrongholdRoomCrossing func_35346_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, -4, -1, 0, 11, 7, 11, var5);
		return func_35319_a(var7) && StructureComponent.func_35312_a(var0, var7) == null ? new ComponentStrongholdRoomCrossing(var6, var1, var7, var5) : null;
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.func_35295_a(var1, var3)) {
			return false;
		} else {
			this.func_35307_a(var1, var3, 0, 0, 0, 10, 6, 10, true, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35323_a(var1, var2, var3, this.field_35349_a, 4, 1, 0);
			this.func_35294_a(var1, var3, 4, 1, 10, 6, 3, 10, 0, 0, false);
			this.func_35294_a(var1, var3, 0, 1, 4, 0, 3, 6, 0, 0, false);
			this.func_35294_a(var1, var3, 10, 1, 4, 10, 3, 6, 0, 0, false);
			int var4;
			switch(this.field_35347_b) {
			case 0:
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 5, 1, 5, var3);
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 5, 2, 5, var3);
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 5, 3, 5, var3);
				this.func_35309_a(var1, Block.torchWood.blockID, 0, 4, 3, 5, var3);
				this.func_35309_a(var1, Block.torchWood.blockID, 0, 6, 3, 5, var3);
				this.func_35309_a(var1, Block.torchWood.blockID, 0, 5, 3, 4, var3);
				this.func_35309_a(var1, Block.torchWood.blockID, 0, 5, 3, 6, var3);
				this.func_35309_a(var1, Block.stairSingle.blockID, 0, 4, 1, 4, var3);
				this.func_35309_a(var1, Block.stairSingle.blockID, 0, 4, 1, 5, var3);
				this.func_35309_a(var1, Block.stairSingle.blockID, 0, 4, 1, 6, var3);
				this.func_35309_a(var1, Block.stairSingle.blockID, 0, 6, 1, 4, var3);
				this.func_35309_a(var1, Block.stairSingle.blockID, 0, 6, 1, 5, var3);
				this.func_35309_a(var1, Block.stairSingle.blockID, 0, 6, 1, 6, var3);
				this.func_35309_a(var1, Block.stairSingle.blockID, 0, 5, 1, 4, var3);
				this.func_35309_a(var1, Block.stairSingle.blockID, 0, 5, 1, 6, var3);
				break;
			case 1:
				for(var4 = 0; var4 < 5; ++var4) {
					this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 3, 1, 3 + var4, var3);
					this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 7, 1, 3 + var4, var3);
					this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 3 + var4, 1, 3, var3);
					this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 3 + var4, 1, 7, var3);
				}

				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 5, 1, 5, var3);
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 5, 2, 5, var3);
				this.func_35309_a(var1, Block.field_35052_bn.blockID, 0, 5, 3, 5, var3);
				this.func_35309_a(var1, Block.waterMoving.blockID, 0, 5, 4, 5, var3);
				break;
			case 2:
				for(var4 = 1; var4 <= 9; ++var4) {
					this.func_35309_a(var1, Block.cobblestone.blockID, 0, 1, 3, var4, var3);
					this.func_35309_a(var1, Block.cobblestone.blockID, 0, 9, 3, var4, var3);
				}

				for(var4 = 1; var4 <= 9; ++var4) {
					this.func_35309_a(var1, Block.cobblestone.blockID, 0, var4, 3, 1, var3);
					this.func_35309_a(var1, Block.cobblestone.blockID, 0, var4, 3, 9, var3);
				}

				this.func_35309_a(var1, Block.cobblestone.blockID, 0, 5, 1, 4, var3);
				this.func_35309_a(var1, Block.cobblestone.blockID, 0, 5, 1, 6, var3);
				this.func_35309_a(var1, Block.cobblestone.blockID, 0, 5, 3, 4, var3);
				this.func_35309_a(var1, Block.cobblestone.blockID, 0, 5, 3, 6, var3);
				this.func_35309_a(var1, Block.cobblestone.blockID, 0, 4, 1, 5, var3);
				this.func_35309_a(var1, Block.cobblestone.blockID, 0, 6, 1, 5, var3);
				this.func_35309_a(var1, Block.cobblestone.blockID, 0, 4, 3, 5, var3);
				this.func_35309_a(var1, Block.cobblestone.blockID, 0, 6, 3, 5, var3);

				for(var4 = 1; var4 <= 3; ++var4) {
					this.func_35309_a(var1, Block.cobblestone.blockID, 0, 4, var4, 4, var3);
					this.func_35309_a(var1, Block.cobblestone.blockID, 0, 6, var4, 4, var3);
					this.func_35309_a(var1, Block.cobblestone.blockID, 0, 4, var4, 6, var3);
					this.func_35309_a(var1, Block.cobblestone.blockID, 0, 6, var4, 6, var3);
				}

				this.func_35309_a(var1, Block.torchWood.blockID, 0, 5, 3, 5, var3);

				for(var4 = 2; var4 <= 8; ++var4) {
					this.func_35309_a(var1, Block.planks.blockID, 0, 2, 3, var4, var3);
					this.func_35309_a(var1, Block.planks.blockID, 0, 3, 3, var4, var3);
					if(var4 <= 3 || var4 >= 7) {
						this.func_35309_a(var1, Block.planks.blockID, 0, 4, 3, var4, var3);
						this.func_35309_a(var1, Block.planks.blockID, 0, 5, 3, var4, var3);
						this.func_35309_a(var1, Block.planks.blockID, 0, 6, 3, var4, var3);
					}

					this.func_35309_a(var1, Block.planks.blockID, 0, 7, 3, var4, var3);
					this.func_35309_a(var1, Block.planks.blockID, 0, 8, 3, var4, var3);
				}

				this.func_35309_a(var1, Block.ladder.blockID, this.func_35301_c(Block.ladder.blockID, 4), 9, 1, 3, var3);
				this.func_35309_a(var1, Block.ladder.blockID, this.func_35301_c(Block.ladder.blockID, 4), 9, 2, 3, var3);
				this.func_35309_a(var1, Block.ladder.blockID, this.func_35301_c(Block.ladder.blockID, 4), 9, 3, 3, var3);
				this.func_35299_a(var1, var3, var2, 3, 4, 8, field_35348_c, 1 + var2.nextInt(4));
			}

			return true;
		}
	}
}
