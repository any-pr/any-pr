package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentVillageHouse4_Garden extends ComponentVillage {
	private int field_35403_a = -1;
	private final boolean field_35402_b;

	public ComponentVillageHouse4_Garden(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35317_h = var4;
		this.field_35316_g = var3;
		this.field_35402_b = var2.nextBoolean();
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
	}

	public static ComponentVillageHouse4_Garden func_35401_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		StructureBoundingBox var7 = StructureBoundingBox.func_35663_a(var2, var3, var4, 0, 0, 0, 5, 6, 5, var5);
		return StructureComponent.func_35312_a(var0, var7) != null ? null : new ComponentVillageHouse4_Garden(var6, var1, var7, var5);
	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.field_35403_a < 0) {
			this.field_35403_a = this.func_35367_b(var1, var3);
			if(this.field_35403_a < 0) {
				return true;
			}

			this.field_35316_g.func_35670_a(0, this.field_35403_a - this.field_35316_g.field_35675_e + 6 - 1, 0);
		}

		this.func_35294_a(var1, var3, 0, 0, 0, 4, 0, 4, Block.cobblestone.blockID, Block.cobblestone.blockID, false);
		this.func_35294_a(var1, var3, 0, 4, 0, 4, 4, 4, Block.wood.blockID, Block.wood.blockID, false);
		this.func_35294_a(var1, var3, 1, 4, 1, 3, 4, 3, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 0, 1, 0, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 0, 2, 0, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 0, 3, 0, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 4, 1, 0, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 4, 2, 0, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 4, 3, 0, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 0, 1, 4, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 0, 2, 4, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 0, 3, 4, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 4, 1, 4, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 4, 2, 4, var3);
		this.func_35309_a(var1, Block.cobblestone.blockID, 0, 4, 3, 4, var3);
		this.func_35294_a(var1, var3, 0, 1, 1, 0, 3, 3, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 4, 1, 1, 4, 3, 3, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35294_a(var1, var3, 1, 1, 4, 3, 3, 4, Block.planks.blockID, Block.planks.blockID, false);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 0, 2, 2, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 2, 2, 4, var3);
		this.func_35309_a(var1, Block.field_35049_br.blockID, 0, 4, 2, 2, var3);
		this.func_35309_a(var1, Block.planks.blockID, 0, 1, 1, 0, var3);
		this.func_35309_a(var1, Block.planks.blockID, 0, 1, 2, 0, var3);
		this.func_35309_a(var1, Block.planks.blockID, 0, 1, 3, 0, var3);
		this.func_35309_a(var1, Block.planks.blockID, 0, 2, 3, 0, var3);
		this.func_35309_a(var1, Block.planks.blockID, 0, 3, 3, 0, var3);
		this.func_35309_a(var1, Block.planks.blockID, 0, 3, 2, 0, var3);
		this.func_35309_a(var1, Block.planks.blockID, 0, 3, 1, 0, var3);
		if(this.func_35297_a(var1, 2, 0, -1, var3) == 0 && this.func_35297_a(var1, 2, -1, -1, var3) != 0) {
			this.func_35309_a(var1, Block.stairCompactCobblestone.blockID, this.func_35301_c(Block.stairCompactCobblestone.blockID, 3), 2, 0, -1, var3);
		}

		this.func_35294_a(var1, var3, 1, 1, 1, 3, 3, 3, 0, 0, false);
		if(this.field_35402_b) {
			this.func_35309_a(var1, Block.fence.blockID, 0, 0, 5, 0, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 1, 5, 0, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 2, 5, 0, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 3, 5, 0, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 4, 5, 0, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 0, 5, 4, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 1, 5, 4, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 2, 5, 4, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 3, 5, 4, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 4, 5, 4, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 4, 5, 1, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 4, 5, 2, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 4, 5, 3, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 0, 5, 1, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 0, 5, 2, var3);
			this.func_35309_a(var1, Block.fence.blockID, 0, 0, 5, 3, var3);
		}

		int var4;
		if(this.field_35402_b) {
			var4 = this.func_35301_c(Block.ladder.blockID, 3);
			this.func_35309_a(var1, Block.ladder.blockID, var4, 3, 1, 3, var3);
			this.func_35309_a(var1, Block.ladder.blockID, var4, 3, 2, 3, var3);
			this.func_35309_a(var1, Block.ladder.blockID, var4, 3, 3, 3, var3);
			this.func_35309_a(var1, Block.ladder.blockID, var4, 3, 4, 3, var3);
		}

		this.func_35309_a(var1, Block.torchWood.blockID, 0, 2, 3, 1, var3);

		for(var4 = 0; var4 < 5; ++var4) {
			for(int var5 = 0; var5 < 5; ++var5) {
				this.func_35314_b(var1, var5, 6, var4, var3);
				this.func_35303_b(var1, Block.cobblestone.blockID, 0, var5, -1, var4, var3);
			}
		}

		return true;
	}
}
