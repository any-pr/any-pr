package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentStrongholdLibrary extends ComponentStronghold {
	private static final StructurePieceTreasure[] field_35335_b = new StructurePieceTreasure[]{new StructurePieceTreasure(Item.book.shiftedIndex, 0, 1, 3, 20), new StructurePieceTreasure(Item.paper.shiftedIndex, 0, 2, 7, 20), new StructurePieceTreasure(Item.map.shiftedIndex, 0, 1, 1, 1), new StructurePieceTreasure(Item.compass.shiftedIndex, 0, 1, 1, 1)};
	protected final EnumDoor field_35337_a;
	private final boolean field_35336_c;

	public ComponentStrongholdLibrary(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35337_a = this.func_35322_a(var2);
		this.field_35316_g = var3;
		this.field_35336_c = var3.func_35668_c() > 6;
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
	}

	public static ComponentStrongholdLibrary func_35334_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, -4, -1, 0, 14, 11, 15, var5);
		if(!func_35319_a(var7) || StructureComponent.func_35312_a(var0, var7) != null) {
			var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, -4, -1, 0, 14, 6, 15, var5);
			if(!func_35319_a(var7) || StructureComponent.func_35312_a(var0, var7) != null) {
				return null;
			}
		}

		return new ComponentStrongholdLibrary(var6, var1, var7, var5);
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.func_35295_a(var1, var3)) {
			return false;
		} else {
			byte var4 = 11;
			if(!this.field_35336_c) {
				var4 = 6;
			}

			this.func_35307_a(var1, var3, 0, 0, 0, 13, var4 - 1, 14, true, var2, StructureStrongholdPieces.func_35622_b());
			this.func_35323_a(var1, var2, var3, this.field_35337_a, 4, 1, 0);
			this.func_35315_a(var1, var3, var2, 0.07F, 2, 1, 1, 11, 4, 13, Block.web.blockID, Block.web.blockID, false);

			int var7;
			for(var7 = 1; var7 <= 13; ++var7) {
				if((var7 - 1) % 4 == 0) {
					this.func_35294_a(var1, var3, 1, 1, var7, 1, 4, var7, Block.planks.blockID, Block.planks.blockID, false);
					this.func_35294_a(var1, var3, 12, 1, var7, 12, 4, var7, Block.planks.blockID, Block.planks.blockID, false);
					this.func_35309_a(var1, Block.torchWood.blockID, 0, 2, 3, var7, var3);
					this.func_35309_a(var1, Block.torchWood.blockID, 0, 11, 3, var7, var3);
					if(this.field_35336_c) {
						this.func_35294_a(var1, var3, 1, 6, var7, 1, 9, var7, Block.planks.blockID, Block.planks.blockID, false);
						this.func_35294_a(var1, var3, 12, 6, var7, 12, 9, var7, Block.planks.blockID, Block.planks.blockID, false);
					}
				} else {
					this.func_35294_a(var1, var3, 1, 1, var7, 1, 4, var7, Block.bookShelf.blockID, Block.bookShelf.blockID, false);
					this.func_35294_a(var1, var3, 12, 1, var7, 12, 4, var7, Block.bookShelf.blockID, Block.bookShelf.blockID, false);
					if(this.field_35336_c) {
						this.func_35294_a(var1, var3, 1, 6, var7, 1, 9, var7, Block.bookShelf.blockID, Block.bookShelf.blockID, false);
						this.func_35294_a(var1, var3, 12, 6, var7, 12, 9, var7, Block.bookShelf.blockID, Block.bookShelf.blockID, false);
					}
				}
			}

			for(var7 = 3; var7 < 12; var7 += 2) {
				this.func_35294_a(var1, var3, 3, 1, var7, 4, 3, var7, Block.bookShelf.blockID, Block.bookShelf.blockID, false);
				this.func_35294_a(var1, var3, 6, 1, var7, 7, 3, var7, Block.bookShelf.blockID, Block.bookShelf.blockID, false);
				this.func_35294_a(var1, var3, 9, 1, var7, 10, 3, var7, Block.bookShelf.blockID, Block.bookShelf.blockID, false);
			}

			if(this.field_35336_c) {
				this.func_35294_a(var1, var3, 1, 5, 1, 3, 5, 13, Block.planks.blockID, Block.planks.blockID, false);
				this.func_35294_a(var1, var3, 10, 5, 1, 12, 5, 13, Block.planks.blockID, Block.planks.blockID, false);
				this.func_35294_a(var1, var3, 4, 5, 1, 9, 5, 2, Block.planks.blockID, Block.planks.blockID, false);
				this.func_35294_a(var1, var3, 4, 5, 12, 9, 5, 13, Block.planks.blockID, Block.planks.blockID, false);
				this.func_35309_a(var1, Block.planks.blockID, 0, 9, 5, 11, var3);
				this.func_35309_a(var1, Block.planks.blockID, 0, 8, 5, 11, var3);
				this.func_35309_a(var1, Block.planks.blockID, 0, 9, 5, 10, var3);
				this.func_35294_a(var1, var3, 3, 6, 2, 3, 6, 12, Block.fence.blockID, Block.fence.blockID, false);
				this.func_35294_a(var1, var3, 10, 6, 2, 10, 6, 10, Block.fence.blockID, Block.fence.blockID, false);
				this.func_35294_a(var1, var3, 4, 6, 2, 9, 6, 2, Block.fence.blockID, Block.fence.blockID, false);
				this.func_35294_a(var1, var3, 4, 6, 12, 8, 6, 12, Block.fence.blockID, Block.fence.blockID, false);
				this.func_35309_a(var1, Block.fence.blockID, 0, 9, 6, 11, var3);
				this.func_35309_a(var1, Block.fence.blockID, 0, 8, 6, 11, var3);
				this.func_35309_a(var1, Block.fence.blockID, 0, 9, 6, 10, var3);
				var7 = this.func_35301_c(Block.ladder.blockID, 3);
				this.func_35309_a(var1, Block.ladder.blockID, var7, 10, 1, 13, var3);
				this.func_35309_a(var1, Block.ladder.blockID, var7, 10, 2, 13, var3);
				this.func_35309_a(var1, Block.ladder.blockID, var7, 10, 3, 13, var3);
				this.func_35309_a(var1, Block.ladder.blockID, var7, 10, 4, 13, var3);
				this.func_35309_a(var1, Block.ladder.blockID, var7, 10, 5, 13, var3);
				this.func_35309_a(var1, Block.ladder.blockID, var7, 10, 6, 13, var3);
				this.func_35309_a(var1, Block.ladder.blockID, var7, 10, 7, 13, var3);
				byte var8 = 7;
				byte var9 = 7;
				this.func_35309_a(var1, Block.fence.blockID, 0, var8 - 1, 9, var9, var3);
				this.func_35309_a(var1, Block.fence.blockID, 0, var8, 9, var9, var3);
				this.func_35309_a(var1, Block.fence.blockID, 0, var8 - 1, 8, var9, var3);
				this.func_35309_a(var1, Block.fence.blockID, 0, var8, 8, var9, var3);
				this.func_35309_a(var1, Block.fence.blockID, 0, var8 - 1, 7, var9, var3);
				this.func_35309_a(var1, Block.fence.blockID, 0, var8, 7, var9, var3);
				this.func_35309_a(var1, Block.fence.blockID, 0, var8 - 2, 7, var9, var3);
				this.func_35309_a(var1, Block.fence.blockID, 0, var8 + 1, 7, var9, var3);
				this.func_35309_a(var1, Block.fence.blockID, 0, var8 - 1, 7, var9 - 1, var3);
				this.func_35309_a(var1, Block.fence.blockID, 0, var8 - 1, 7, var9 + 1, var3);
				this.func_35309_a(var1, Block.fence.blockID, 0, var8, 7, var9 - 1, var3);
				this.func_35309_a(var1, Block.fence.blockID, 0, var8, 7, var9 + 1, var3);
				this.func_35309_a(var1, Block.torchWood.blockID, 0, var8 - 2, 8, var9, var3);
				this.func_35309_a(var1, Block.torchWood.blockID, 0, var8 + 1, 8, var9, var3);
				this.func_35309_a(var1, Block.torchWood.blockID, 0, var8 - 1, 8, var9 - 1, var3);
				this.func_35309_a(var1, Block.torchWood.blockID, 0, var8 - 1, 8, var9 + 1, var3);
				this.func_35309_a(var1, Block.torchWood.blockID, 0, var8, 8, var9 - 1, var3);
				this.func_35309_a(var1, Block.torchWood.blockID, 0, var8, 8, var9 + 1, var3);
			}

			this.func_35299_a(var1, var3, var2, 3, 3, 5, field_35335_b, 1 + var2.nextInt(4));
			if(this.field_35336_c) {
				this.func_35309_a(var1, 0, 0, 12, 9, 1, var3);
				this.func_35299_a(var1, var3, var2, 12, 8, 1, field_35335_b, 1 + var2.nextInt(4));
			}

			return true;
		}
	}
}
