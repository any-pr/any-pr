package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class StructureMineshaftPieces {
	private static final StructurePieceTreasure[] field_35436_a = new StructurePieceTreasure[]{new StructurePieceTreasure(Item.ingotIron.shiftedIndex, 0, 1, 5, 10), new StructurePieceTreasure(Item.ingotGold.shiftedIndex, 0, 1, 3, 5), new StructurePieceTreasure(Item.redstone.shiftedIndex, 0, 4, 9, 5), new StructurePieceTreasure(Item.dyePowder.shiftedIndex, 4, 4, 9, 5), new StructurePieceTreasure(Item.diamond.shiftedIndex, 0, 1, 2, 3), new StructurePieceTreasure(Item.coal.shiftedIndex, 0, 3, 8, 10), new StructurePieceTreasure(Item.bread.shiftedIndex, 0, 1, 3, 15), new StructurePieceTreasure(Item.pickaxeSteel.shiftedIndex, 0, 1, 1, 1), new StructurePieceTreasure(Block.rail.blockID, 0, 4, 8, 1), new StructurePieceTreasure(Item.field_35412_bf.shiftedIndex, 0, 2, 4, 10)};

	private static StructureComponent func_35434_a(List var0, Random var1, int var2, int var3, int var4, int var5, int var6) {
		int var7 = var1.nextInt(100);
		StructureBoundingBox var8;
		if(var7 >= 80) {
			var8 = ComponentMineshaftCross.func_35362_a(var0, var1, var2, var3, var4, var5);
			if(var8 != null) {
				return new ComponentMineshaftCross(var6, var1, var8, var5);
			}
		} else if(var7 >= 70) {
			var8 = ComponentMineshaftStairs.func_35365_a(var0, var1, var2, var3, var4, var5);
			if(var8 != null) {
				return new ComponentMineshaftStairs(var6, var1, var8, var5);
			}
		} else {
			var8 = ComponentMineshaftCorridor.func_35357_a(var0, var1, var2, var3, var4, var5);
			if(var8 != null) {
				return new ComponentMineshaftCorridor(var6, var1, var8, var5);
			}
		}

		return null;
	}

	private static StructureComponent func_35435_b(StructureComponent var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		if(var7 > 10) {
			return null;
		} else if(Math.abs(var3 - var0.func_35313_b().field_35678_a) <= 80 && Math.abs(var5 - var0.func_35313_b().field_35677_c) <= 80) {
			StructureComponent var8 = func_35434_a(var1, var2, var3, var4, var5, var6, var7 + 1);
			if(var8 != null) {
				var1.add(var8);
				var8.func_35308_a(var0, var1, var2);
			}

			return var8;
		} else {
			return null;
		}
	}

	static StructureComponent func_35433_a(StructureComponent var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		return func_35435_b(var0, var1, var2, var3, var4, var5, var6, var7);
	}

	static StructurePieceTreasure[] func_35432_a() {
		return field_35436_a;
	}
}
